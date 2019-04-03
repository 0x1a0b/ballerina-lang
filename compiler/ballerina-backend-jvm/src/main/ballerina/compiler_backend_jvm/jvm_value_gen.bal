// Copyright (c) 2019 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
//
// WSO2 Inc. licenses this file to you under the Apache License,
// Version 2.0 (the "License"); you may not use this file except
// in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

public type ObjectGenerator object {

    private bir:Package module;
    private bir:BObjectType? currentObjectType = ();

    public function __init(bir:Package module) {
        self.module = module;
    }

    public function generateValueClasses(bir:TypeDef?[] typeDefs, map<byte[]> jarEntries) {
        foreach var optionalTypeDef in typeDefs {
            bir:TypeDef typeDef = getTypeDef(optionalTypeDef);
            bir:BType bType = typeDef.typeValue;
            if (bType is bir:BObjectType) {
                self.currentObjectType = bType;
                string className = self.getObjectValueClassName(typeDef.name.value);
                byte[] bytes = self.createObjectValueClass(bType, className, typeDef);
                jarEntries[className + ".class"] = bytes;
            }
        }
    }

    // Private methods

    private function getObjectValueClassName(string objTypeName) returns string {
        return getPackageName(self.module.org.value, self.module.name.value) + objTypeName;
    }

    private function createObjectValueClass(bir:BObjectType objectType, string className, bir:TypeDef typeDef) 
            returns byte[] {
        jvm:ClassWriter cw = new(COMPUTE_FRAMES);
        cw.visit(V1_8, ACC_PUBLIC + ACC_SUPER, className, (), ABSTRACT_OBJECT_VALUE, [OBJECT_VALUE]);

        bir:BObjectField?[] fields = objectType.fields;
        self.createObjectFields(cw, fields);

        bir:Function?[]? attachedFuncs = typeDef.attachedFuncs;
        if (attachedFuncs is bir:Function?[]) {
            self.createObjectMethods(cw, attachedFuncs);
        }

        self.createInit(cw);
        self.createCallMethod(cw, objectType.attachedFunctions, className);
        self.createGetMethod(cw, fields, className);
        self.createSetMethod(cw, fields, className);
        cw.visitEnd();
        return cw.toByteArray();
    }

    private function createObjectFields(jvm:ClassWriter cw, bir:BObjectField?[] fields) {
        foreach var field in fields {
            if (field is bir:BObjectField) {
                jvm:FieldVisitor fv = cw.visitField(0, field.name.value, getTypeDesc(field.typeValue));
                fv.visitEnd();
            }
        }
    }

    private function createObjectMethods(jvm:ClassWriter cw, bir:Function?[] attachedFuncs) {
        foreach var func in attachedFuncs {
            if (func is bir:Function) {
                generateMethod(func, cw, self.module, true);
            }
        }
    }

    private function createInit(jvm:ClassWriter cw) {
        jvm:MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>", io:sprintf("(L%s;)V", BTYPE), (), ());
        mv.visitCode();

        // load super
        mv.visitVarInsn(ALOAD, 0);
        // load type
        mv.visitVarInsn(ALOAD, 1);
        // invoke super(type);
        mv.visitMethodInsn(INVOKESPECIAL, ABSTRACT_OBJECT_VALUE, "<init>", io:sprintf("(L%s;)V", BTYPE), false);

        mv.visitInsn(RETURN);
        mv.visitMaxs(5, 5);
        mv.visitEnd();
    }

    private function createCallMethod(jvm:ClassWriter cw, bir:BAttachedFunction?[] funcs, string className) {
        jvm:MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "call",
                io:sprintf("(L%s;L%s;[L%s;)L%s;", STRAND, STRING_VALUE, OBJECT, OBJECT), (), ());
        mv.visitCode();

        int funcNameRegIndex = 2;
        jvm:Label defaultCaseLabel = new jvm:Label();
        sortByHash(funcs);
        jvm:Label[] labels = self.createLabelsforSwitch(mv, funcNameRegIndex, funcs, defaultCaseLabel);
        jvm:Label[] targetLabels = self.createLabelsForEqualCheck(mv, funcNameRegIndex, funcs, labels, 
                defaultCaseLabel);

        // case body
        int i = 0;
        foreach var optionalFunc in funcs {
            bir:BAttachedFunction func = self.getFunction(optionalFunc);
            jvm:Label targetLabel = targetLabels[i];
            mv.visitLabel(targetLabel);

            // load self
            mv.visitVarInsn(ALOAD, 0);

            // load strand
            mv.visitVarInsn(ALOAD, 1);

            bir:BType[] paramTypes = func.funcType.paramTypes;
            int j = 0;
            foreach var paramType in paramTypes {
                // load parameters
                mv.visitVarInsn(ALOAD, 3);

                // load j'th parameter
                mv.visitLdcInsn(j);
                mv.visitInsn(L2I);
                mv.visitInsn(AALOAD);
                addUnboxInsn(mv, paramType);
                j += 1;
            }

            // use index access, since retType can be nil.
            bir:BType? retType = func.funcType["retType"];
            string methodSig = getMethodDesc(paramTypes, retType);
            mv.visitMethodInsn(INVOKEVIRTUAL, className, self.getName(func), methodSig, false);

            if (retType is () || retType is bir:BTypeNil) {
                mv.visitInsn(ACONST_NULL);
            } else {
                addBoxInsn(mv, retType);
            }

            mv.visitInsn(ARETURN);
            i += 1;
        }

        self.createDefaultCase(mv, defaultCaseLabel);
        mv.visitMaxs(funcs.length() + 10, funcs.length() + 10);
        mv.visitEnd();
    }

    private function createGetMethod(jvm:ClassWriter cw, bir:BObjectField?[] fields, string className) {
        jvm:MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "get",
                io:sprintf("(L%s;)L%s;", STRING_VALUE, OBJECT), (), ());
        mv.visitCode();

        int fieldNameRegIndex = 1;
        jvm:Label defaultCaseLabel = new jvm:Label();
        jvm:Label[] labels = self.createLabelsforSwitch(mv, fieldNameRegIndex, fields, defaultCaseLabel);
        jvm:Label[] targetLabels = self.createLabelsForEqualCheck(mv, fieldNameRegIndex, fields, labels, 
                defaultCaseLabel);

        int i = 0;
        foreach var optionalField in fields {
            bir:BObjectField field = getObjectField(optionalField);
            jvm:Label targetLabel = targetLabels[i];
            mv.visitLabel(targetLabel);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, className, field.name.value, getTypeDesc(field.typeValue));
            addBoxInsn(mv, field.typeValue);
            mv.visitInsn(ARETURN);
            i += 1;
        }

        self.createDefaultCase(mv, defaultCaseLabel);
        mv.visitMaxs(fields.length() + 10, fields.length() + 10);
        mv.visitEnd();
    }

    private function createSetMethod(jvm:ClassWriter cw, bir:BObjectField?[] fields, string className) {
        jvm:MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "set",
                io:sprintf("(L%s;L%s;)V", STRING_VALUE, OBJECT),
                (), ());
        mv.visitCode();

        int fieldNameRegIndex = 1;
        jvm:Label defaultCaseLabel = new jvm:Label();
        jvm:Label[] labels = self.createLabelsforSwitch(mv, fieldNameRegIndex, fields, defaultCaseLabel);
        jvm:Label[] targetLabels = self.createLabelsForEqualCheck(mv, fieldNameRegIndex, fields, labels, 
                defaultCaseLabel);

        // case body
        int i = 0;
        foreach var optionalField in fields {
            bir:BObjectField field = getObjectField(optionalField);
            jvm:Label targetLabel = targetLabels[i];
            mv.visitLabel(targetLabel);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ALOAD, 2);
            addUnboxInsn(mv, field.typeValue);
            mv.visitFieldInsn(PUTFIELD, className, field.name.value, getTypeDesc(field.typeValue));
            mv.visitInsn(RETURN);
            i += 1;
        }

        self.createDefaultCase(mv, defaultCaseLabel);
        mv.visitMaxs(fields.length() + 10, fields.length() + 10);
        mv.visitEnd();
    }

    private function createLabelsforSwitch(jvm:MethodVisitor mv, int nameRegIndex, NamedNode?[] nodes, 
            jvm:Label defaultCaseLabel) returns jvm:Label[] {
        mv.visitVarInsn(ALOAD, nameRegIndex);
        mv.visitMethodInsn(INVOKEVIRTUAL, STRING_VALUE, "hashCode", "()I", false);

        // Create labels for the cases
        int i = 0;
        jvm:Label[] labels = [];
        int[] hashCodes = [];
        foreach var node in nodes {
            if (node is NamedNode) {
                labels[i] = new jvm:Label();
                hashCodes[i] = self.getName(node).hashCode();
                i += 1;
            }
        }
        mv.visitLookupSwitchInsn(defaultCaseLabel, hashCodes, labels);
        return labels;
    }

    private function createDefaultCase(jvm:MethodVisitor mv, jvm:Label defaultCaseLabel) {
        mv.visitLabel(defaultCaseLabel);
        mv.visitTypeInsn(NEW, "java/lang/RuntimeException");
        mv.visitInsn(DUP);
        mv.visitLdcInsn("error!");
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/RuntimeException", "<init>",
                io:sprintf("(L%s;)V", STRING_VALUE), false);
        mv.visitInsn(ATHROW);
    }

    private function createLabelsForEqualCheck(jvm:MethodVisitor mv, int nameRegIndex, NamedNode?[] nodes,
            jvm:Label[] labels, jvm:Label defaultCaseLabel) returns jvm:Label[] {
        jvm:Label[] targetLabels = [];
        int i = 0;
        foreach var node in nodes {
            if (node is NamedNode) {
                mv.visitLabel(labels[i]);
                mv.visitVarInsn(ALOAD, nameRegIndex);
                mv.visitLdcInsn(self.getName(node));
                mv.visitMethodInsn(INVOKEVIRTUAL, STRING_VALUE, "equals",
                        io:sprintf("(L%s;)Z", OBJECT), false);
                jvm:Label targetLabel = new jvm:Label();
                mv.visitJumpInsn(IFNE, targetLabel);
                mv.visitJumpInsn(GOTO, defaultCaseLabel);
                targetLabels[i] = targetLabel;
                i += 1;
            }
        }

        return targetLabels;
    }

    private function getFunction(bir:BAttachedFunction? func) returns bir:BAttachedFunction {
        if (func is bir:BAttachedFunction) {
            return func;
        } else {
            error err = error(io:sprintf("Invalid function: %s", func));
            panic err;
        }
    }

    // TODO: this function should accept a `NamedNode`. However there seems to be a bug
    // in type compatibility check.
    function getName(any node) returns string {
        bir:BObjectType? objectType = self.currentObjectType;
        if (node is bir:BAttachedFunction && objectType is bir:BObjectType) {
            return objectType.name.value + "_" + node.name.value;
        } else if (node is NamedNode) {
            return node.name.value;
        } else {
            error err = error(io:sprintf("Invalid node: %s", node));
            panic err;
        }
    }
};


// --------------------- Sorting ---------------------------

type NamedNode record {
    bir:Name name = {};
};

function sortByHash(NamedNode?[] arr) {
    quickSort(arr, 0, arr.length() - 1);
}

function quickSort(NamedNode?[] arr, int low, int high) { 
    if (low < high) { 
        // pi is partitioning index, arr[pi] is now at right place
        int pi = partition(arr, low, high); 

        // Recursively sort elements before partition and after partition 
        quickSort(arr, low, pi - 1); 
        quickSort(arr, pi + 1, high); 
    } 
} 

function partition(NamedNode?[] arr, int begin, int end) returns int {
    int pivot = getHash(arr[end]);
    int i = begin - 1;

    int j = begin;
    while (j < end) {
        if (getHash(arr[j]) <= pivot) {
            i += 1;
            swap(arr, i, j);
        }
        j += 1;
    }
    swap(arr, i+1, end);
    return i + 1;
}

function getHash(NamedNode? node) returns int {
    if (node is NamedNode) {
        return node.name.value.hashCode();
    } else {
        error err = error(io:sprintf("Invalid sortable node: %s", node));
        panic err;
    }
}

function swap(NamedNode?[] arr, int i, int j) {
    NamedNode? swapTemp = arr[i];
    arr[i] = arr[j];
    arr[j] = swapTemp;
}
