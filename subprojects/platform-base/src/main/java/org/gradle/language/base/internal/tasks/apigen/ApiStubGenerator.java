/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.language.base.internal.tasks.apigen;

import org.objectweb.asm.*;

import java.util.List;

public class ApiStubGenerator {

    private final List<String> allowedPackages;

    public ApiStubGenerator(List<String> allowedPackages) {
        this.allowedPackages = allowedPackages;
    }

    public byte[] convertToApi(byte[] clazz) {
        ClassReader cr = new ClassReader(clazz);
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cr.accept(new PublicAPIAdapter(cw), ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return cw.toByteArray();
    }

    private class PublicAPIAdapter extends ClassVisitor implements Opcodes {

        public static final String UOE_METHOD = "$unsupportedOpEx";
        private String className;

        public PublicAPIAdapter(ClassVisitor cv) {
            super(ASM5, cv);
        }


        /**
         * Generates an exception which is going to be thrown in each method. The reason it is in a separate method
         * is because it reduces the bytecode size.
         */
        private void generateUnsupportedOperationExceptionMethod() {
            MethodVisitor mv = cv.visitMethod(ACC_PRIVATE | ACC_STATIC | ACC_SYNTHETIC, UOE_METHOD, "()Ljava/lang/UnsupportedOperationException;", null, null);
            mv.visitCode();
            mv.visitTypeInsn(NEW, "java/lang/UnsupportedOperationException");
            mv.visitInsn(DUP);
            mv.visitLdcInsn("You tried to call a method on an API class. You probably added the API jar on classpath instead of the implementation jar.");
            mv.visitMethodInsn(INVOKESPECIAL, "java/lang/UnsupportedOperationException", "<init>", "(Ljava/lang/String;)V", false);
            mv.visitInsn(ARETURN);
            mv.visitMaxs(3, 0);
            mv.visitEnd();
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            super.visit(version, access, name, signature, superName, interfaces);
            className = name;
            if ((access & ACC_INTERFACE) == 0) {
                generateUnsupportedOperationExceptionMethod();
            }
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            if ("<clinit>".equals(name)) {
                // discard static initializers
                return null;
            }
            if ((access & ACC_PUBLIC) == ACC_PUBLIC || (access & ACC_PROTECTED) == ACC_PROTECTED) {
                validateSignature(desc);
                MethodVisitor mv = cv.visitMethod(access, name, desc, signature, exceptions);
                if ((access & ACC_ABSTRACT) != ACC_ABSTRACT) {
                    mv.visitCode();
                    mv.visitMethodInsn(INVOKESTATIC, className, UOE_METHOD, "()Ljava/lang/UnsupportedOperationException;", false);
                    mv.visitInsn(ATHROW);
                    mv.visitMaxs(1, 0);
                    mv.visitEnd();
                }
            }
            return null;
        }

        private void validateSignature(String desc) {
            if (allowedPackages.isEmpty()) {
                return;
            }
            Type returnType = Type.getReturnType(desc);
            validateType(returnType);
            Type[] argumentTypes = Type.getArgumentTypes(desc);
            for (Type argumentType : argumentTypes) {
                validateType(argumentType);
            }
        }

        private void validateType(Type type) {
            if (allowedPackages.isEmpty()) {
                return;
            }
            String className = type.getClassName();
            if (isPrimitiveType(className)) {
                return;
            }
            if (type.getElementType()!=null) {
                validateType(type.getElementType());
                return;
            }
            if (className.startsWith("java")) {
                // special case to treat all Java classes as belonging to the public API
                return;
            }
            boolean allowed = false;
            for (String allowedPackage : allowedPackages) {
                if (className.startsWith(allowedPackage+".")) {
                    allowed = true;
                    break;
                }
            }
            if (!allowed) {
                throw new InvalidPublicAPIException("Type "+className + " is exposed in the public API but doesn't belong to the allowed packages");
            }
        }

        private boolean isPrimitiveType(String className) {
            return "void".equals(className) || "byte".equals(className) || "short".equals(className)
                || "int".equals(className) || "boolean".equals(className) || "long".equals(className)
                || "char".equals(className) || "float".equals(className) || "double".equals(className);
        }

        @Override
        public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
            if ((access & ACC_PUBLIC) == ACC_PUBLIC || (access & ACC_PROTECTED) == ACC_PROTECTED) {
                return cv.visitField(access, name, desc, signature, value);
            }
            return null;
        }

        @Override
        public void visitInnerClass(String name, String outerName, String innerName, int access) {
            if ((access & ACC_PUBLIC) == ACC_PUBLIC || (access & ACC_SUPER) == ACC_SUPER) {
                super.visitInnerClass(name, outerName, innerName, access);
            }
        }
    }

}
