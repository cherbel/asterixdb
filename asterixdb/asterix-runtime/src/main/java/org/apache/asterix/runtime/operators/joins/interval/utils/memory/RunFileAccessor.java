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
package org.apache.asterix.runtime.operators.joins.interval.utils.memory;

import org.apache.hyracks.api.comm.IFrameTupleAccessor;
import org.apache.hyracks.api.exceptions.HyracksDataException;

import java.nio.ByteBuffer;

public class RunFileAccessor implements ITupleCursor {

    @Override
    public boolean exists() {
        return false;
    }

    @Override
    public void next() throws HyracksDataException {

    }

    @Override public int getTupleId() {
        return 0;
    }

    @Override public void reset(ByteBuffer byteBuffer) {

    }

    @Override
    public IFrameTupleAccessor getAccessor() {
        return null;
    }
}
