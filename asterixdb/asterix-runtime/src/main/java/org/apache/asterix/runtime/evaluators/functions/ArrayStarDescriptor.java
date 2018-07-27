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
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.asterix.builders.IAsterixListBuilder;
import org.apache.asterix.builders.OrderedListBuilder;
import org.apache.asterix.builders.RecordBuilder;
import org.apache.asterix.om.functions.BuiltinFunctions;
import org.apache.asterix.om.functions.IFunctionDescriptor;
import org.apache.asterix.om.functions.IFunctionDescriptorFactory;
import org.apache.asterix.om.functions.IFunctionTypeInferer;
import org.apache.asterix.om.pointables.ARecordVisitablePointable;
import org.apache.asterix.om.pointables.PointableAllocator;
import org.apache.asterix.om.pointables.base.DefaultOpenFieldType;
import org.apache.asterix.om.pointables.base.IVisitablePointable;
import org.apache.asterix.om.types.ATypeTag;
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
import org.apache.hyracks.data.std.api.IPointable;
import org.apache.hyracks.data.std.primitive.VoidPointable;
import org.apache.hyracks.data.std.util.ArrayBackedValueStorage;
import org.apache.hyracks.dataflow.common.data.accessors.IFrameTupleReference;

public class ArrayStarDescriptor extends AbstractScalarFunctionDynamicDescriptor {
    private static final long serialVersionUID = 1L;

    public static final IFunctionDescriptorFactory FACTORY = new IFunctionDescriptorFactory() {
        @Override
        public IFunctionDescriptor createFunctionDescriptor() {
            return new ArrayStarDescriptor();
        }

        @Override
        public IFunctionTypeInferer createFunctionTypeInferer() {
            return FunctionTypeInferers.SET_ARGUMENT_TYPE;
        }
    };

    private IAType inputListType;

    @Override
    public FunctionIdentifier getIdentifier() {
        return BuiltinFunctions.ARRAY_STAR;
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
                return new ArrayStarEval(args, ctx);
            }
        };
    }

    public class UTF8StringComparator implements Comparator<IVisitablePointable> {
        private final IBinaryComparator comp = PointableHelper.createStringBinaryComparator();

        @Override
        public int compare(IVisitablePointable val1, IVisitablePointable val2) {
            try {
                return PointableHelper.compareStringBinValues(val1, val2, comp);
            } catch (HyracksDataException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    public class ArrayStarEval implements IScalarEvaluator {
        private final UTF8StringComparator comp = new UTF8StringComparator();
        private final ArrayBackedValueStorage storage;
        private final IScalarEvaluator listEval;
        private final IPointable list;
        private final IPointable object;
        private final CastTypeEvaluator caster;
        private final ListAccessor listAccessor;
        private final TreeMap<IVisitablePointable, IVisitablePointable[]> fieldNameToValues;
        private final RecordBuilder recordBuilder;
        private final IAsterixListBuilder listBuilder;
        private final PointableAllocator pointableAllocator;

        public ArrayStarEval(IScalarEvaluatorFactory[] args, IHyracksTaskContext ctx) throws HyracksDataException {
            storage = new ArrayBackedValueStorage();
            object = new VoidPointable();
            list = new VoidPointable();
            listEval = args[0].createScalarEvaluator(ctx);
            caster = new CastTypeEvaluator();
            listAccessor = new ListAccessor();
            fieldNameToValues = new TreeMap<>(comp);
            recordBuilder = new RecordBuilder();
            listBuilder = new OrderedListBuilder();
            pointableAllocator = new PointableAllocator();
        }

        @Override
        public void evaluate(IFrameTupleReference tuple, IPointable result) throws HyracksDataException {
            storage.reset();
            listEval.evaluate(tuple, list);
            ATypeTag listTag = ATYPETAGDESERIALIZER.deserialize(list.getByteArray()[list.getStartOffset()]);
            if (listTag != ATypeTag.ARRAY) {
                PointableHelper.setNull(result);
                return;
            }

            caster.reset(DefaultOpenFieldType.NESTED_OPEN_AORDERED_LIST_TYPE, inputListType, listEval);
            caster.evaluate(tuple, list);

            fieldNameToValues.clear();
            listAccessor.reset(list.getByteArray(), list.getStartOffset());
            int numObjects = listAccessor.size();
            try {
                for (int objectIndex = 0; objectIndex < numObjects; objectIndex++) {
                    listAccessor.getOrWriteItem(objectIndex, object, storage);
                    processObject(object, objectIndex, numObjects);
                }

                if (fieldNameToValues.isEmpty()) {
                    PointableHelper.setMissing(result);
                    return;
                }

                recordBuilder.reset(DefaultOpenFieldType.NESTED_OPEN_RECORD_TYPE);
                recordBuilder.init();

                for (Map.Entry<IVisitablePointable, IVisitablePointable[]> e : fieldNameToValues.entrySet()) {
                    listBuilder.reset(DefaultOpenFieldType.NESTED_OPEN_AORDERED_LIST_TYPE);
                    for (int i = 0; i < e.getValue().length; i++) {
                        if (e.getValue()[i] == null) {
                            listBuilder.addItem(PointableHelper.NULL_REF);
                        } else {
                            listBuilder.addItem(e.getValue()[i]);
                        }
                    }
                    storage.reset();
                    listBuilder.write(storage.getDataOutput(), true);
                    recordBuilder.addField(e.getKey(), storage);
                }

                storage.reset();
                recordBuilder.write(storage.getDataOutput(), true);
                result.set(storage);
            } catch (IOException e) {
                throw HyracksDataException.create(e);
            } finally {
                pointableAllocator.reset();
            }
        }

        private void processObject(IPointable object, int objectIndex, int numObjects) {
            ARecordVisitablePointable record;
            // process only objects (records)
            if (object.getByteArray()[object.getStartOffset()] == ATypeTag.SERIALIZED_RECORD_TYPE_TAG) {
                record = pointableAllocator.allocateRecordValue(DefaultOpenFieldType.NESTED_OPEN_RECORD_TYPE);
                record.set(object.getByteArray(), object.getStartOffset(), object.getLength());

                List<IVisitablePointable> fieldNames = record.getFieldNames();
                List<IVisitablePointable> fieldValues = record.getFieldValues();
                IVisitablePointable[] values;
                for (int j = 0; j < fieldNames.size(); j++) {
                    values = fieldNameToValues.get(fieldNames.get(j));
                    if (values == null) {
                        values = new IVisitablePointable[numObjects];
                        fieldNameToValues.put(fieldNames.get(j), values);
                    }
                    values[objectIndex] = fieldValues.get(j);
                }
            }
        }
    }
}