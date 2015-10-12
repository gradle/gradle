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

import com.google.common.collect.Sets;
import org.objectweb.asm.*;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;

import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.List;
import java.util.Set;

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
         * Generates an exception which is going to be thrown in each method. The reason it is in a separate method is because it reduces the bytecode size.
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
                Set<String> invalidReferencedTypes = invalidReferencedTypes(signature == null ? desc : signature);
                if (invalidReferencedTypes.isEmpty()) {
                    MethodVisitor mv = cv.visitMethod(access, name, desc, signature, exceptions);
                    if ((access & ACC_ABSTRACT) != ACC_ABSTRACT) {
                        mv.visitCode();
                        mv.visitMethodInsn(INVOKESTATIC, className, UOE_METHOD, "()Ljava/lang/UnsupportedOperationException;", false);
                        mv.visitInsn(ATHROW);
                        mv.visitMaxs(1, 0);
                        mv.visitEnd();
                    }
                } else {
                    StringBuilder methodDesc = new StringBuilder();
                    methodDesc.append(Modifier.toString(access)).append(" ");
                    methodDesc.append(Type.getReturnType(desc).getClassName()).append(" ");
                    methodDesc.append(name);
                    methodDesc.append("(");
                    Type[] argumentTypes = Type.getArgumentTypes(desc);
                    for (int i = 0, argumentTypesLength = argumentTypes.length; i < argumentTypesLength; i++) {
                        Type type = argumentTypes[i];
                        methodDesc.append(type.getClassName());
                        if (i < argumentTypesLength - 1) {
                            methodDesc.append(", ");
                        }
                    }
                    methodDesc.append(")");
                    if (invalidReferencedTypes.size() == 1) {
                        throw new InvalidPublicAPIException(String.format("In %s, type %s is exposed in the public API but doesn't belong to the allowed packages.", methodDesc, invalidReferencedTypes.iterator().next()));
                    } else {
                        StringBuilder sb = new StringBuilder("The following types are referenced in ");
                        sb.append(methodDesc);
                        sb.append(" but don't belong to the allowed packages:\n");
                        for (String invalidReferencedType : invalidReferencedTypes) {
                            sb.append("   - ").append(invalidReferencedType).append("\n");
                        }
                        throw new InvalidPublicAPIException(sb.toString());
                    }
                }
            }
            return null;
        }

        private Set<String> invalidReferencedTypes(String signature) {
            if (allowedPackages.isEmpty()) {
                return Collections.emptySet();
            }
            SignatureReader sr = new SignatureReader(signature);
            final Set<String> result = Sets.newLinkedHashSet();
            sr.accept(new SignatureVisitor(Opcodes.ASM5) {
                @Override
                public void visitClassType(String name) {
                    super.visitClassType(name);
                    String className = name.replace('/', '.');
                    if (!validateType(className)) {
                        result.add(className);
                    }
                }
            });
            return result;
        }

        private boolean validateType(String className) {
            if (allowedPackages.isEmpty()) {
                return true;
            }

            String pkg = className.indexOf(".") > 0 ? className.substring(0, className.lastIndexOf(".")) : "";

            if (pkg.startsWith("java")) {
                // special case to treat all Java classes as belonging to the public API
                return true;
            }
            boolean allowed = false;
            for (String allowedPackage : allowedPackages) {
                if (pkg.equals(allowedPackage)) {
                    allowed = true;
                    break;
                }
            }
            return allowed;
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
