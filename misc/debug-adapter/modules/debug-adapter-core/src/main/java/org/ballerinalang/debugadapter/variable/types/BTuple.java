/*
 * Copyright (c) 2020, WSO2 Inc. (http://wso2.com) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ballerinalang.debugadapter.variable.types;

import com.sun.jdi.ArrayReference;
import com.sun.jdi.Field;
import com.sun.jdi.IntegerValue;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;
import org.ballerinalang.debugadapter.variable.BCompoundVariable;
import org.ballerinalang.debugadapter.variable.BVariableType;
import org.ballerinalang.debugadapter.variable.VariableContext;
import org.ballerinalang.debugadapter.variable.VariableUtils;
import org.eclipse.lsp4j.debug.Variable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.ballerinalang.debugadapter.variable.VariableUtils.UNKNOWN_VALUE;
import static org.ballerinalang.debugadapter.variable.VariableUtils.getStringFrom;

/**
 * Ballerina tuple variable type.
 */
public class BTuple extends BCompoundVariable {

    public BTuple(VariableContext context, Value value, Variable dapVariable) {
        super(context, BVariableType.TUPLE, value, dapVariable);
    }

    @Override
    public String computeValue() {
        try {
            return getTupleType(jvmValue);
        } catch (Exception e) {
            return UNKNOWN_VALUE;
        }
    }

    @Override
    public Map<String, Value> computeChildVariables() {
        try {
            if (!(jvmValue instanceof ObjectReference)) {
                return new HashMap<>();
            }
            ObjectReference jvmValueRef = (ObjectReference) jvmValue;
            Field valueField = jvmValueRef.referenceType().fieldByName("refValues");
            List<Value> valueList = ((ArrayReference) jvmValueRef.getValue(valueField)).getValues();

            // List length is 100 by default. Create a sub list with actual array size.
            List<Value> valueSubList = valueList.subList(0, getTupleSize(jvmValueRef));
            Map<String, Value> values = new TreeMap<>();
            AtomicInteger nextVarIndex = new AtomicInteger(0);
            valueSubList.forEach(item -> {
                int varIndex = nextVarIndex.getAndIncrement();
                values.put("[" + varIndex + "]", valueSubList.get(varIndex));
            });
            return values;
        } catch (Exception ignored) {
            return new HashMap<>();
        }
    }

    /**
     * Returns the tuple type in string form.
     */
    private String getTupleType(Value jvmValue) {
        try {
            Optional<Value> tupleType = VariableUtils.getFieldValue(jvmValue, "tupleType");
            if (!tupleType.isPresent()) {
                return UNKNOWN_VALUE;
            }
            Optional<Value> subTypes = VariableUtils.getFieldValue(tupleType.get(), "tupleTypes");
            if (!subTypes.isPresent()) {
                return UNKNOWN_VALUE;
            }
            Optional<Value> typesArray = VariableUtils.getFieldValue(subTypes.get(), "elementData");
            if (!typesArray.isPresent()) {
                return UNKNOWN_VALUE;
            }
            List<Value> subValues = ((ArrayReference) typesArray.get()).getValues();
            StringJoiner tupleTypes = new StringJoiner(",");
            subValues.forEach(ref -> {
                if (ref instanceof ObjectReference) {
                    Field typeNameField = ((ObjectReference) ref).referenceType().fieldByName("typeName");
                    Value typeNameRef = ((ObjectReference) ref).getValue(typeNameField);
                    tupleTypes.add(getStringFrom(typeNameRef));
                }
            });
            return String.format("tuple[%s]", tupleTypes.toString());
        } catch (Exception e) {
            return UNKNOWN_VALUE;
        }
    }

    /**
     * Returns the size/length of a given ballerina tuple typed variable.
     *
     * @param arrayRef object reference of the tuple instance.
     * @return size of the tuple.
     */
    private int getTupleSize(ObjectReference arrayRef) {
        List<Field> fields = arrayRef.referenceType().allFields();
        Field arraySizeField = arrayRef.getValues(fields).entrySet().stream().filter(fieldValueEntry ->
                fieldValueEntry.getValue() != null &&
                        fieldValueEntry.getKey().toString().endsWith("ArrayValue.size"))
                .map(Map.Entry::getKey).collect(Collectors.toList()).get(0);
        return ((IntegerValue) arrayRef.getValue(arraySizeField)).value();
    }
}
