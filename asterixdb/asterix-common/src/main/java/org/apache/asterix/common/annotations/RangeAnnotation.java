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
package org.apache.asterix.common.annotations;

import org.apache.hyracks.algebricks.core.algebra.expressions.AbstractExpressionAnnotation;
import org.apache.hyracks.algebricks.core.algebra.expressions.IExpressionAnnotation;
import org.apache.hyracks.dataflow.common.data.partition.range.RangeMap;

public class RangeAnnotation extends AbstractExpressionAnnotation {

    private static final String RANGE_HINT_STRING = "range";
    public static final RangeAnnotation INSTANCE = new RangeAnnotation();

    private RangeMap map;

    @Override
    public IExpressionAnnotation copy() {
        RangeAnnotation clone = new RangeAnnotation();
        clone.setObject(object);
        return clone;
    }

    public void setRangeMap(RangeMap map) {
        this.map = map;
    }

    public RangeMap getRangeMap() {
        return map;
    }

    public static boolean isRangeHint(String hint) {
        return hint.startsWith(RANGE_HINT_STRING);
    }

    public static int getHintLength(String hint) {
        if (hint.startsWith(RANGE_HINT_STRING)) {
            return RANGE_HINT_STRING.length();
        }
        return 0;
    }

}