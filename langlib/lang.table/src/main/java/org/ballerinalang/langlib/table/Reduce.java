/*
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.ballerinalang.langlib.table;

import org.ballerinalang.jvm.BRuntime;
import org.ballerinalang.jvm.scheduling.Strand;
import org.ballerinalang.jvm.values.FPValue;
import org.ballerinalang.jvm.values.TableValueImpl;
import org.ballerinalang.model.types.TypeKind;
import org.ballerinalang.natives.annotations.Argument;
import org.ballerinalang.natives.annotations.BallerinaFunction;
import org.ballerinalang.natives.annotations.ReturnType;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.ballerinalang.util.BLangCompilerConstants.TABLE_VERSION;

/**
 * Native implementation of lang.table:reduce(table&lt;Type&gt;, function).
 *
 * @since 1.3.0
 */
@BallerinaFunction(
        orgName = "ballerina", packageName = "lang.table", version = TABLE_VERSION, functionName = "reduce",
        args = {@Argument(name = "tbl", type = TypeKind.TABLE), @Argument(name = "func", type = TypeKind.FUNCTION),
                @Argument(name = "initial", type = TypeKind.ANY)},
        returnType = {@ReturnType(type = TypeKind.ANY)},
        isPublic = true
)
public class Reduce {

    public static Object reduce(Strand strand, TableValueImpl tbl, FPValue<Object, Object> func, Object initial) {
        int size = tbl.values().size();
        AtomicReference<Object> accum = new AtomicReference<>(initial);
        AtomicInteger index = new AtomicInteger(-1);
        BRuntime.getCurrentRuntime()
                .invokeFunctionPointerAsyncIteratively(func, size,
                        () -> new Object[]{strand, accum.get(), true,
                                tbl.get(tbl.getKeys()[index.incrementAndGet()]), true},
                        accum::set, accum::get);
        return accum.get();
    }

    public static Object reduce_bstring(Strand strand, TableValueImpl tbl,
                                        FPValue<Object, Object> func, Object initial) {
        return reduce(strand, tbl, func, initial);
    }
}
