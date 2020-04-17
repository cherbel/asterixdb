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
package org.apache.asterix.runtime.operators.joins;

import org.apache.hyracks.api.context.IHyracksTaskContext;
import org.apache.hyracks.api.dataflow.value.IRangePartitionType.RangePartitioningType;
import org.apache.hyracks.api.exceptions.HyracksDataException;
import org.apache.hyracks.dataflow.common.data.partition.range.RangeMap;

//import org.apache.hyracks.dataflow.std.misc.RangeForwardOperatorDescriptor.RangeForwardTaskState;

public class OverlappingIntervalMergeJoinCheckerFactory extends AbstractIntervalMergeJoinCheckerFactory {
    private static final long serialVersionUID = 1L;
    private final RangeMap rangeMap;

    public OverlappingIntervalMergeJoinCheckerFactory(RangeMap rangeMap) {
        this.rangeMap = rangeMap;
    }

    @Override
    public IIntervalMergeJoinChecker createMergeJoinChecker(int[] keys0, int[] keys1, IHyracksTaskContext ctx)
            throws HyracksDataException {
        //        int fieldIndex = 0;
        //        RangeForwardTaskState rangeState = RangeForwardTaskState.getRangeState(rangeId.getId(), ctx);
        //        IRangeMap rangeMap = rangeState.getRangeMap();
        //        if (ATypeTag.INT64.serialize() != rangeMap.getTag(0, 0)) {
        //            throw new HyracksDataException("Invalid range map type for interval merge join checker.");
        //        }
        //        int nPartitions = rangeState.getNumberOfPartitions();
        //        int partition = ctx.getTaskAttemptId().getTaskId().getPartition();
        //        int slot = rangeMap.getMinSlotFromPartition(partition, nPartitions);
        //
        //        long partitionStart = 0;
        //        // All lookups are on typed values.
        //        if (slot < 0) {
        //            partitionStart = LongPointable.getLong(rangeMap.getMinByteArray(fieldIndex),
        //                    rangeMap.getMinStartOffset(fieldIndex) + 1);
        //        } else if (slot <= rangeMap.getSplitCount()) {
        //            partitionStart = LongPointable.getLong(rangeMap.getByteArray(fieldIndex, slot),
        //                    rangeMap.getStartOffset(fieldIndex, slot) + 1);
        //        } else if (slot > rangeMap.getSplitCount()) {
        //            partitionStart = LongPointable.getLong(rangeMap.getMaxByteArray(fieldIndex),
        //                    rangeMap.getMaxStartOffset(fieldIndex) + 1);
        //        }
        return new OverlappingIntervalMergeJoinChecker(keys0, keys1, 0);
    }

    @Override
    public IIntervalMergeJoinChecker createInverseMergeJoinChecker(int[] keys0, int[] keys1, IHyracksTaskContext ctx)
            throws HyracksDataException {
        return createMergeJoinChecker(keys0, keys1, ctx);
    }

    @Override
    public RangePartitioningType getRightPartitioningType() {
        return RangePartitioningType.SPLIT;
    }

}
