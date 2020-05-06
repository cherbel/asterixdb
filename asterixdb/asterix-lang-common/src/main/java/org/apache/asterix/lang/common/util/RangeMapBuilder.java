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
package org.apache.asterix.lang.common.util;

import static org.apache.asterix.om.base.temporal.ADateParserFactory.parseDatePart;
import static org.apache.asterix.om.base.temporal.ADateTimeParserFactory.parseDateTimePart;
import static org.apache.asterix.om.base.temporal.ATimeParserFactory.parseTimePart;

import java.io.DataOutput;
import java.util.List;

import org.apache.asterix.common.exceptions.CompilationException;
import org.apache.asterix.common.exceptions.ErrorCode;
import org.apache.asterix.common.exceptions.RuntimeDataException;
import org.apache.asterix.formats.nontagged.BinaryComparatorFactoryProvider;
import org.apache.asterix.formats.nontagged.SerializerDeserializerProvider;
import org.apache.asterix.lang.common.base.Expression;
import org.apache.asterix.lang.common.base.Expression.Kind;
import org.apache.asterix.lang.common.base.Literal;
import org.apache.asterix.lang.common.expression.CallExpr;
import org.apache.asterix.lang.common.expression.ListConstructor;
import org.apache.asterix.lang.common.expression.LiteralExpr;
import org.apache.asterix.lang.common.literal.DoubleLiteral;
import org.apache.asterix.lang.common.literal.FloatLiteral;
import org.apache.asterix.lang.common.literal.IntegerLiteral;
import org.apache.asterix.lang.common.literal.LongIntegerLiteral;
import org.apache.asterix.lang.common.literal.StringLiteral;
import org.apache.asterix.om.base.*;
import org.apache.asterix.om.types.ATypeTag;
import org.apache.asterix.om.types.BuiltinType;
import org.apache.hyracks.algebricks.common.exceptions.NotImplementedException;
import org.apache.hyracks.api.dataflow.value.IBinaryComparator;
import org.apache.hyracks.api.dataflow.value.IBinaryComparatorFactory;
import org.apache.hyracks.api.dataflow.value.ISerializerDeserializer;
import org.apache.hyracks.api.exceptions.HyracksDataException;
import org.apache.hyracks.api.exceptions.HyracksException;
import org.apache.hyracks.api.exceptions.SourceLocation;
import org.apache.hyracks.data.std.util.ArrayBackedValueStorage;
import org.apache.hyracks.dataflow.common.data.partition.range.RangeMap;

public class RangeMapBuilder {

    private RangeMapBuilder() {
    }

    public static RangeMap parseHint(Expression expression) throws CompilationException {
        if (expression.getKind() != Kind.LIST_CONSTRUCTOR_EXPRESSION) {
            throw new CompilationException("The range hint must be a list.");
        }

        ArrayBackedValueStorage abvs = new ArrayBackedValueStorage();
        DataOutput out = abvs.getDataOutput();

        List<Expression> el = ((ListConstructor) expression).getExprList();
        int[] offsets = new int[el.size()];

        // Loop over list of literals
        for (int i = 0; i < el.size(); ++i) {
            Expression item = el.get(i);
            if (item.getKind() == Kind.LITERAL_EXPRESSION) {
                parseLiteralToBytes((LiteralExpr) item, out);
                offsets[i] = abvs.getLength();
            } else if (item.getKind() == Kind.CALL_EXPRESSION) {
                parseIntervalToBytes((CallExpr) item, out);
            } else {
                throw new CompilationException("Expected literal in the range hint");
            }
            // TODO Add support for composite fields.
        }

        return new RangeMap(1, abvs.getByteArray(), offsets);
    }

    private static void parseIntervalToBytes(CallExpr item, DataOutput out) throws CompilationException {

        try {

            if (!item.getFunctionSignature().getName().equals("interval")) {
                throw new CompilationException("The range hint must be a list.");
            }

            List<Expression> exprList = item.getExprList();

            if (exprList.size() != 2) {
                throw new CompilationException("Interval does not have the correct number of arguments");
            }

            Expression arg1 = exprList.get(0);
            Expression arg2 = exprList.get(1);

            if (arg1.getKind() != Kind.CALL_EXPRESSION || arg2.getKind() != Kind.CALL_EXPRESSION) {
                throw new CompilationException("Argument must be Call Expression: arg1 && arg2");
            }

            CallExpr arg1Expr = (CallExpr) arg1;
            CallExpr arg2Expr = (CallExpr) arg2;
            LiteralExpr arg1LiteralExpr, arg2LiteralExpr;
            Literal arg1Literal, arg2Literal;
            String arg1Value, arg2Value;
            long intervalStart, intervalEnd;
            byte typeTag;

            switch (arg1Expr.getFunctionSignature().getName()) {
                case "date":
                    arg1LiteralExpr = (LiteralExpr) arg1Expr.getExprList().get(0);
                    arg1Literal = arg1LiteralExpr.getValue();
                    arg1Value = arg1Literal.getStringValue();
                    intervalStart = parseDatePart(arg1Value, 0, arg1Value.length());
                    break;
                case "time":
                    arg1LiteralExpr = (LiteralExpr) arg1Expr.getExprList().get(0);
                    arg1Literal = arg1LiteralExpr.getValue();
                    arg1Value = arg1Literal.getStringValue();
                    intervalStart = parseTimePart(arg1Value, 0, arg1Value.length());
                    break;
                case "datetime":
                    arg1LiteralExpr = (LiteralExpr) arg1Expr.getExprList().get(0);
                    arg1Literal = arg1LiteralExpr.getValue();
                    arg1Value = arg1Literal.getStringValue();
                    intervalStart = parseDateTimePart(arg1Value, 0, arg1Value.length());
                    break;
                default:
                    throw new NotImplementedException("The range map builder has not been implemented for "
                            + arg1Expr.getFunctionSignature().getName() + " type of expressions.");
            }

            switch (arg2Expr.getFunctionSignature().getName()) {
                case "date":
                    arg2LiteralExpr = (LiteralExpr) arg2Expr.getExprList().get(0);
                    arg2Literal = arg2LiteralExpr.getValue();
                    arg2Value = arg2Literal.getStringValue();
                    intervalEnd = parseDatePart(arg2Value, 0, arg2Value.length());
                    typeTag = ATypeTag.DATE.serialize();
                    break;
                case "time":
                    arg2LiteralExpr = (LiteralExpr) arg2Expr.getExprList().get(0);
                    arg2Literal = arg2LiteralExpr.getValue();
                    arg2Value = arg2Literal.getStringValue();
                    intervalEnd = parseTimePart(arg2Value, 0, arg2Value.length());
                    typeTag = ATypeTag.TIME.serialize();
                    break;
                case "datetime":
                    arg2LiteralExpr = (LiteralExpr) arg2Expr.getExprList().get(0);
                    arg2Literal = arg2LiteralExpr.getValue();
                    arg2Value = arg2Literal.getStringValue();
                    intervalEnd = parseDateTimePart(arg2Value, 0, arg2Value.length());
                    typeTag = ATypeTag.DATETIME.serialize();
                    break;
                default:
                    throw new NotImplementedException("The range map builder has not been implemented for "
                            + arg2Expr.getFunctionSignature().getName() + " type of expressions.");
            }

            //Interval and Serialize
            AInterval interval = new AInterval(intervalStart, intervalEnd, typeTag);
            ISerializerDeserializer serde =
                    SerializerDeserializerProvider.INSTANCE.getSerializerDeserializer(BuiltinType.AINTERVAL);
            serde.serialize(interval, out);

        } catch (HyracksException e) {
            throw new CompilationException(ErrorCode.RANGE_MAP_ERROR, e, item.getSourceLocation(), e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static void parseLiteralToBytes(LiteralExpr item, DataOutput out) throws CompilationException {
        AMutableDouble aDouble = new AMutableDouble(0);
        AMutableFloat aFloat = new AMutableFloat(0);
        AMutableInt64 aInt64 = new AMutableInt64(0);
        AMutableInt32 aInt32 = new AMutableInt32(0);
        AMutableString aString = new AMutableString("");
        @SuppressWarnings("rawtypes")
        ISerializerDeserializer serde;

        Literal l = item.getValue();
        try {
            switch (l.getLiteralType()) {
                case DOUBLE:
                    DoubleLiteral dl = (DoubleLiteral) l;
                    serde = SerializerDeserializerProvider.INSTANCE.getSerializerDeserializer(BuiltinType.ADOUBLE);
                    aDouble.setValue(dl.getValue());
                    serde.serialize(aDouble, out);
                    break;
                case FLOAT:
                    FloatLiteral fl = (FloatLiteral) l;
                    serde = SerializerDeserializerProvider.INSTANCE.getSerializerDeserializer(BuiltinType.AFLOAT);
                    aFloat.setValue(fl.getValue());
                    serde.serialize(aFloat, out);
                    break;
                case INTEGER:
                    IntegerLiteral il = (IntegerLiteral) l;
                    serde = SerializerDeserializerProvider.INSTANCE.getSerializerDeserializer(BuiltinType.AINT32);
                    aInt32.setValue(il.getValue());
                    serde.serialize(aInt32, out);
                    break;
                case LONG:
                    LongIntegerLiteral lil = (LongIntegerLiteral) l;
                    serde = SerializerDeserializerProvider.INSTANCE.getSerializerDeserializer(BuiltinType.AINT64);
                    aInt64.setValue(lil.getValue());
                    serde.serialize(aInt64, out);
                    break;
                case STRING:
                    StringLiteral sl = (StringLiteral) l;
                    serde = SerializerDeserializerProvider.INSTANCE.getSerializerDeserializer(BuiltinType.ASTRING);
                    aString.setValue(sl.getValue());
                    serde.serialize(aString, out);
                    break;
                default:
                    throw new NotImplementedException("The range map builder has not been implemented for "
                            + item.getKind() + " type of expressions.");
            }
        } catch (HyracksDataException e) {
            throw new CompilationException(ErrorCode.RANGE_MAP_ERROR, e, item.getSourceLocation(), e.getMessage());
        }
    }

    public static void verifyRangeOrder(RangeMap rangeMap, boolean ascending, SourceLocation sourceLoc)
            throws CompilationException {
        // TODO Add support for composite fields.
        int fieldIndex = 0;
        int fieldType = rangeMap.getTag(0, 0);
        BinaryComparatorFactoryProvider comparatorFactory = BinaryComparatorFactoryProvider.INSTANCE;
        IBinaryComparatorFactory bcf;
        try {
            bcf = comparatorFactory.getBinaryComparatorFactory(ATypeTag.VALUE_TYPE_MAPPING[fieldType], ascending);
        } catch (RuntimeDataException e) {
            throw new CompilationException(ErrorCode.COMPILATION_ERROR, sourceLoc, e.getMessage());
        }
        IBinaryComparator comparator = bcf.createBinaryComparator();
        int c = 0;
        for (int split = 1; split < rangeMap.getSplitCount(); ++split) {
            if (fieldType != rangeMap.getTag(fieldIndex, split)) {
                throw new CompilationException(ErrorCode.COMPILATION_ERROR, sourceLoc,
                        "Range field contains more than a single type of items (" + fieldType + " and "
                                + rangeMap.getTag(fieldIndex, split) + ").");
            }
            int previousSplit = split - 1;
            try {
                c = comparator.compare(rangeMap.getByteArray(), rangeMap.getStartOffset(fieldIndex, previousSplit),
                        rangeMap.getLength(fieldIndex, previousSplit), rangeMap.getByteArray(),
                        rangeMap.getStartOffset(fieldIndex, split), rangeMap.getLength(fieldIndex, split));
            } catch (HyracksDataException e) {
                throw new CompilationException(ErrorCode.COMPILATION_ERROR, sourceLoc, e.getMessage());
            }
            if (c >= 0) {
                throw new CompilationException(ErrorCode.COMPILATION_ERROR, sourceLoc,
                        "Range fields are not in sorted order.");
            }
        }
    }
}
