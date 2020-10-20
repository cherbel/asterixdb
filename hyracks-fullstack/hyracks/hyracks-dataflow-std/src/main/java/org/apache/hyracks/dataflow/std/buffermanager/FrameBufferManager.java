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

package org.apache.hyracks.dataflow.std.buffermanager;

import java.nio.ByteBuffer;
import java.util.ArrayList;

import org.apache.hyracks.api.comm.IFrameTupleAccessor;
import org.apache.hyracks.api.dataflow.value.RecordDescriptor;
import org.apache.hyracks.api.exceptions.HyracksDataException;
import org.apache.hyracks.dataflow.common.comm.io.FrameTupleAccessor;
import org.apache.hyracks.dataflow.std.structures.TuplePointer;

/**
 * General Frame based buffer manager class
 */
public class FrameBufferManager implements IFrameBufferManager {

    ArrayList<ByteBuffer> buffers = new ArrayList<>();

    @Override
    public void reset() throws HyracksDataException {
        buffers.clear();
    }

    @Override
    public BufferInfo getFrame(int frameIndex, BufferInfo returnedInfo) {
        returnedInfo.reset(buffers.get(frameIndex), 0, buffers.get(frameIndex).capacity());
        return returnedInfo;
    }

    @Override
    public int getNumFrames() {
        return buffers.size();
    }

    @Override
    public int insertFrame(ByteBuffer frame) throws HyracksDataException {
        buffers.add(frame);
        return buffers.size() - 1;
    }

    @Override
    public void close() {
        buffers = null;
    }

    @Override
    public void resetIterator() {

    }

    @Override
    public int next() {
        return 0;
    }

    @Override
    public boolean exists() {
        return false;
    }

    @Override
    public ITupleAccessor getTupleAccessor(final RecordDescriptor recordDescriptor) {
        return new AbstractTupleAccessor() {
            FrameTupleAccessor innerAccessor = new FrameTupleAccessor(recordDescriptor);

            @Override
            IFrameTupleAccessor getInnerAccessor() {
                return innerAccessor;
            }

            @Override
            void resetInnerAccessor(TuplePointer tuplePointer) {
                //                buffers[parsePartitionId(tuplePointer.getFrameIndex())]
                //                        .getFrame(parseFrameIdInPartition(tuplePointer.getFrameIndex()), tempInfo);
                //                innerAccessor.reset(tempInfo.getBuffer(), tempInfo.getStartOffset(), tempInfo.getLength());
            }

            @Override
            void resetInnerAccessor(int frameIndex) {
                //                partitionArray[parsePartitionId(frameIndex)].getFrame(parseFrameIdInPartition(frameIndex), tempInfo);
                //                innerAccessor.reset(tempInfo.getBuffer(), tempInfo.getStartOffset(), tempInfo.getLength());
            }

            @Override
            int getFrameCount() {
                return 0;
                //                return partitionArray.length;
            }
        };
    }

    protected int parsePartitionId(int externalFrameId) {
        return externalFrameId % getNumPartitions();
    }

    public int getNumPartitions() {
        return 0;
    }

}
