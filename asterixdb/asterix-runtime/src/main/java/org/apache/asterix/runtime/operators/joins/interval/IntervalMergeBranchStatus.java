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
package org.apache.asterix.runtime.operators.joins.interval;

import java.io.Serializable;

public class IntervalMergeBranchStatus implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Stage {
        UNKNOWN,
        OPENED,
        DATA_PROCESSING,
        JOIN_PROCESSING,
        CLOSED;

        public boolean isEqualOrBefore(Stage bs) {
            return this.ordinal() <= bs.ordinal();
        }
    }

    private boolean hasMore = true;

    private Stage stage = Stage.UNKNOWN;

    private boolean runFileWriting = true;
    private boolean runFileReading = true;

    public Stage getStatus() {
        return stage;
    }

    public void setStageOpen() {
        stage = Stage.OPENED;
    }

    public void setStageData() {
        stage = Stage.DATA_PROCESSING;
    }

    public void setStageJoin() {
        stage = Stage.JOIN_PROCESSING;
    }

    public void setStageClose() {
        stage = Stage.CLOSED;
    }

    public boolean hasMore() {
        return hasMore;
    }

    public void noMore() {
        this.hasMore = false;
    }

    public boolean isRunFileWriting() {
        return runFileWriting;
    }

    public void setRunFileWriting(boolean runFileWriting) {
        this.runFileWriting = runFileWriting;
    }

    public boolean isRunFileReading() {
        return runFileReading;
    }

    public void setRunFileReading(boolean runFileReading) {
        this.runFileReading = runFileReading;
    }

    @Override
    public String toString() {
        return "Branch status is " + stage + ": the stream " + (hasMore ? "has more" : "is empty")
                + " and the run file is " + (runFileWriting ? "WRITING " : "") + (runFileReading ? "READING " : "");

    }
}
