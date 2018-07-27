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
package org.apache.asterix.runtime.evaluators.functions;

import static org.apache.asterix.om.types.EnumDeserializer.ATYPETAGDESERIALIZER;

import java.io.IOException;
import java.util.Comparator;
import java.util.PriorityQueue;

import org.apache.asterix.builders.IAsterixListBuilder;
import org.apache.asterix.common.exceptions.ErrorCode;
import org.apache.asterix.common.exceptions.RuntimeDataException;
import org.apache.asterix.dataflow.data.nontagged.comparators.AObjectAscBinaryComparatorFactory;
import org.apache.asterix.om.functions.BuiltinFunctions;
import org.apache.asterix.om.functions.IFunctionDescriptor;
import org.apache.asterix.om.functions.IFunctionDescriptorFactory;
import org.apache.asterix.om.functions.IFunctionTypeInferer;
import org.apache.asterix.om.types.IAType;
import org.apache.asterix.runtime.evaluators.base.AbstractScalarFunctionDynamicDescriptor;
import org.apache.asterix.runtime.evaluators.common.ListAccessor;
import org.apache.asterix.runtime.functions.FunctionTypeInferers;
import org.apache.hyracks.algebricks.common.exceptions.AlgebricksException;
import org.apache.hyracks.algebricks.core.algebra.functions.FunctionIdentifier;
import org.apache.hyracks.algebricks.runtime.base.IScalarEvaluator;
import org.apache.hyracks.algebricks.runtime.base.IScalarEvaluatorFactory;
import org.apache.hyracks.api.context.IHyracksTaskContext;
import org.apache.hyracks.api.dataflow.value.IBinaryComparator;
import org.apache.hyracks.api.exceptions.HyracksDataException;
import org.apache.hyracks.api.exceptions.SourceLocation;
import org.apache.hyracks.data.std.api.IPointable;
import org.apache.hyracks.data.std.util.ArrayBackedValueStorage;

public class ArraySortDescriptor extends AbstractScalarFunctionDynamicDescriptor {
    private static final long serialVersionUID = 1L;
    private IAType inputListType;

    public static final IFunctionDescriptorFactory FACTORY = new IFunctionDescriptorFactory() {
        @Override
        public IFunctionDescriptor createFunctionDescriptor() {
            return new ArraySortDescriptor();
        }

        @Override
        public IFunctionTypeInferer createFunctionTypeInferer() {
            // the type of the input list is needed in order to use the same type for the new returned list
            return FunctionTypeInferers.SET_ARGUMENT_TYPE;
        }
    };

    @Override
    public FunctionIdentifier getIdentifier() {
        return BuiltinFunctions.ARRAY_SORT;
    }

    @Override
    public void setImmutableStates(Object... states) {
        inputListType = (IAType) states[0];
    }

    @Override
    public IScalarEvaluatorFactory createEvaluatorFactory(final IScalarEvaluatorFactory[] args)
            throws AlgebricksException {
        return new IScalarEvaluatorFactory() {
            private static final long serialVersionUID = 1L;

            @Override
            public IScalarEvaluator createScalarEvaluator(final IHyracksTaskContext ctx) throws HyracksDataException {
                return new ArraySortEval(args, ctx, sourceLoc);
            }
        };
    }

    protected class ArraySortComparator implements Comparator<IPointable> {
        private final IBinaryComparator comp = AObjectAscBinaryComparatorFactory.INSTANCE.createBinaryComparator();

        @Override
        public int compare(IPointable val1, IPointable val2) {
            try {
                return comp.compare(val1.getByteArray(), val1.getStartOffset(), val1.getLength(), val2.getByteArray(),
                        val2.getStartOffset(), val2.getLength());
            } catch (HyracksDataException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    public class ArraySortEval extends AbstractArrayProcessEval {
        private final SourceLocation sourceLoc;
        private final PriorityQueue<IPointable> sortedList;
        private IPointable item;
        private ArrayBackedValueStorage storage;

        public ArraySortEval(IScalarEvaluatorFactory[] args, IHyracksTaskContext ctx, SourceLocation sourceLoc)
                throws HyracksDataException {
            super(args, ctx, inputListType);
            this.sourceLoc = sourceLoc;
            item = pointableAllocator.allocateEmpty();
            storage = (ArrayBackedValueStorage) storageAllocator.allocate(null);
            sortedList = new PriorityQueue<>(new ArraySortComparator());
        }

        @Override
        protected void processList(ListAccessor listAccessor, IAsterixListBuilder listBuilder) throws IOException {
            sortedList.clear();
            boolean itemInStorage;
            for (int i = 0; i < listAccessor.size(); i++) {
                itemInStorage = listAccessor.getOrWriteItem(i, item, storage);
                if (ATYPETAGDESERIALIZER.deserialize(item.getByteArray()[item.getStartOffset()]).isDerivedType()) {
                    throw new RuntimeDataException(ErrorCode.CANNOT_COMPARE_COMPLEX, sourceLoc);
                }
                sortedList.add(item);
                if (itemInStorage) {
                    storage = (ArrayBackedValueStorage) storageAllocator.allocate(null);
                }
                item = pointableAllocator.allocateEmpty();
            }
            while (!sortedList.isEmpty()) {
                listBuilder.addItem(sortedList.poll());
            }
        }
    }
}