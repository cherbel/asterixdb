/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.asterix.algebra.operators.physical;

import java.util.ArrayList;
import java.util.List;

import org.apache.asterix.runtime.operators.joins.IIntervalMergeJoinCheckerFactory;
import org.apache.hyracks.algebricks.common.exceptions.AlgebricksException;
import org.apache.hyracks.algebricks.core.algebra.base.*;
import org.apache.hyracks.algebricks.core.algebra.operators.logical.AbstractBinaryJoinOperator.JoinKind;
import org.apache.hyracks.algebricks.core.algebra.operators.logical.AbstractLogicalOperator;
import org.apache.hyracks.algebricks.core.algebra.operators.logical.IOperatorSchema;
import org.apache.hyracks.algebricks.core.algebra.operators.logical.OrderOperator.IOrder.OrderKind;
import org.apache.hyracks.algebricks.core.algebra.operators.physical.AbstractJoinPOperator;
import org.apache.hyracks.algebricks.core.algebra.properties.*;
import org.apache.hyracks.algebricks.core.jobgen.impl.JobGenContext;
import org.apache.hyracks.algebricks.core.jobgen.impl.JobGenHelper;
import org.apache.hyracks.api.dataflow.IOperatorDescriptor;
import org.apache.hyracks.api.dataflow.value.RecordDescriptor;
import org.apache.hyracks.api.job.IOperatorDescriptorRegistry;
import org.apache.hyracks.dataflow.common.data.partition.range.RangeMap;
import org.apache.hyracks.dataflow.std.base.RangeId;

public abstract class AbstractIntervalJoinPOperator extends AbstractJoinPOperator {

    private final List<LogicalVariable> keysLeftBranch;
    private final List<LogicalVariable> keysRightBranch;
    protected final IIntervalMergeJoinCheckerFactory mjcf;
    private final RangeId leftRangeId;
    private final RangeId rightRangeId;
    private final RangeMap rangeMapHint;

    public AbstractIntervalJoinPOperator(JoinKind kind, JoinPartitioningType partitioningType,
            List<LogicalVariable> sideLeftOfEqualities, List<LogicalVariable> sideRightOfEqualities,
            IIntervalMergeJoinCheckerFactory mjcf, RangeId leftRangeId, RangeId rightRangeId, RangeMap rangeMapHint) {
        super(kind, partitioningType);
        this.keysLeftBranch = sideLeftOfEqualities;
        this.keysRightBranch = sideRightOfEqualities;
        this.mjcf = mjcf;
        this.leftRangeId = leftRangeId;
        this.rightRangeId = rightRangeId;
        this.rangeMapHint = rangeMapHint;
    }

    public List<LogicalVariable> getKeysLeftBranch() {
        return keysLeftBranch;
    }

    public List<LogicalVariable> getKeysRightBranch() {
        return keysRightBranch;
    }

    public IIntervalMergeJoinCheckerFactory getIntervalMergeJoinCheckerFactory() {
        return mjcf;
    }

    public RangeId getLeftRangeId() {
        return leftRangeId;
    }

    public RangeId getRightRangeId() {
        return rightRangeId;
    }

    public RangeMap getRangeMapHint() {
        return rangeMapHint;
    }

    @Override
    public PhysicalOperatorTag getOperatorTag() {
        return PhysicalOperatorTag.DELEGATE_OPERATOR;
    }

    @Override
    public String toString() {
        return getIntervalJoin() + " " + keysLeftBranch + " " + keysRightBranch;
    }

    public abstract String getIntervalJoin();

    @Override
    public boolean isMicroOperator() {
        return false;
    }

    @Override
    public void computeDeliveredProperties(ILogicalOperator iop, IOptimizationContext context) {
        ArrayList<OrderColumn> order = getLeftRangeOrderColumn();

        //        (List<OrderColumn> orderColumns, INodeDomain domain, RangeMap rangeMap) {
        //            super(orderColumns, domain, rangeMap);
        IPartitioningProperty pp = new OrderedPartitionedProperty(order, null, rangeMapHint);
        List<ILocalStructuralProperty> propsLocal = new ArrayList<>();
        propsLocal.add(new LocalOrderProperty(getLeftLocalSortOrderColumn()));
        deliveredProperties = new StructuralPropertiesVector(pp, propsLocal);
    }

    @Override
    public PhysicalRequirements getRequiredPropertiesForChildren(ILogicalOperator iop,
            IPhysicalPropertiesVector reqdByParent, IOptimizationContext context) {
        StructuralPropertiesVector[] pv = new StructuralPropertiesVector[2];
        AbstractLogicalOperator op = (AbstractLogicalOperator) iop;

        IPartitioningProperty ppLeft = null;
        List<ILocalStructuralProperty> ispLeft = new ArrayList<>();
        ispLeft.add(new LocalOrderProperty(getLeftLocalSortOrderColumn()));

        IPartitioningProperty ppRight = null;
        List<ILocalStructuralProperty> ispRight = new ArrayList<>();
        ispRight.add(new LocalOrderProperty(getRightLocalSortOrderColumn()));

        if (op.getExecutionMode() == AbstractLogicalOperator.ExecutionMode.PARTITIONED) {
            ppLeft = new OrderedPartitionedProperty(getLeftRangeOrderColumn(), null, rangeMapHint);
            ppRight = new OrderedPartitionedProperty(getRightRangeOrderColumn(), null, rangeMapHint);
        }

        pv[0] = new StructuralPropertiesVector(ppLeft, ispLeft);
        pv[1] = new StructuralPropertiesVector(ppRight, ispRight);
        IPartitioningRequirementsCoordinator prc = IPartitioningRequirementsCoordinator.NO_COORDINATION;
        return new PhysicalRequirements(pv, prc);
    }

    protected ArrayList<OrderColumn> getLeftLocalSortOrderColumn() {
        return getLeftRangeOrderColumn();
    }

    protected ArrayList<OrderColumn> getRightLocalSortOrderColumn() {
        return getRightRangeOrderColumn();
    }

    protected ArrayList<OrderColumn> getLeftRangeOrderColumn() {
        ArrayList<OrderColumn> order = new ArrayList<>();
        for (LogicalVariable v : keysLeftBranch) {
            order.add(new OrderColumn(v, mjcf.isOrderAsc() ? OrderKind.ASC : OrderKind.DESC));
        }
        return order;
    }

    protected ArrayList<OrderColumn> getRightRangeOrderColumn() {
        ArrayList<OrderColumn> orderRight = new ArrayList<>();
        for (LogicalVariable v : keysRightBranch) {
            orderRight.add(new OrderColumn(v, mjcf.isOrderAsc() ? OrderKind.ASC : OrderKind.DESC));
        }
        return orderRight;
    }

    @Override
    public void contributeRuntimeOperator(IHyracksJobBuilder builder, JobGenContext context, ILogicalOperator op,
            IOperatorSchema opSchema, IOperatorSchema[] inputSchemas, IOperatorSchema outerPlanSchema)
            throws AlgebricksException {
        int[] keysLeft = JobGenHelper.variablesToFieldIndexes(keysLeftBranch, inputSchemas[0]);
        int[] keysRight = JobGenHelper.variablesToFieldIndexes(keysRightBranch, inputSchemas[1]);

        IOperatorDescriptorRegistry spec = builder.getJobSpec();
        RecordDescriptor recordDescriptor =
                JobGenHelper.mkRecordDescriptor(context.getTypeEnvironment(op), opSchema, context);

        IOperatorDescriptor opDesc =
                getIntervalOperatorDescriptor(keysLeft, keysRight, spec, recordDescriptor, mjcf, leftRangeId);
        contributeOpDesc(builder, (AbstractLogicalOperator) op, opDesc);

        ILogicalOperator src1 = op.getInputs().get(0).getValue();
        builder.contributeGraphEdge(src1, 0, op, 0);
        ILogicalOperator src2 = op.getInputs().get(1).getValue();
        builder.contributeGraphEdge(src2, 0, op, 1);
    }

    abstract IOperatorDescriptor getIntervalOperatorDescriptor(int[] keysLeft, int[] keysRight,
            IOperatorDescriptorRegistry spec, RecordDescriptor recordDescriptor, IIntervalMergeJoinCheckerFactory mjcf,
            RangeId rangeId);

}
