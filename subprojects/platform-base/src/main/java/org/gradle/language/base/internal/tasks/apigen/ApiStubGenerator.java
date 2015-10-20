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

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

public class ApiStubGenerator {

    // See JLS3 "Binary Compatibility" (13.1)
    private final static Pattern AIC_LOCAL_CLASS_PATTERN = Pattern.compile(".+\\$[0-9]+(?:[\\p{Alnum}_$]+)?$");

    private final boolean validateExposedTypes;
    private final List<String> allowedPackages;
    private final boolean hasDeclaredAPI;

    public ApiStubGenerator(List<String> allowedPackages) {
        this(allowedPackages, false);
    }

    public ApiStubGenerator(List<String> allowedPackages, boolean validateExposedTypes) {
        this.allowedPackages = allowedPackages;
        this.hasDeclaredAPI = !allowedPackages.isEmpty();
        this.validateExposedTypes = validateExposedTypes;
    }

    /**
     * Returns true if the binary class found in parameter is belonging to the API.
     * It will check if the class package is in the list of authorized packages, and if
     * the access flags are ok with regards to the list of packages: if the list is
     * empty, then package private classes are included, whereas if the list is not
     * empty, an API has been declared and the class should be excluded.
     * Therefore, this method should be called on every .class file to process before
     * it is either copied or processed through {@link #convertToApi(byte[])}.
     * @param clazz the bytecode of the class to test
     * @return true if this class should be exposed in the API.
     */
    public boolean belongsToAPI(byte[] clazz) {
        ClassReader cr = new ClassReader(clazz);
        return belongsToApi(cr);
    }

    public boolean belongsToAPI(InputStream inputStream) throws IOException {
        ClassReader cr = new ClassReader(inputStream);
        try {
            return belongsToApi(cr);
        } finally {
            inputStream.close();
        }
    }

    private boolean belongsToApi(ClassReader cr) {
        final AtomicBoolean isAPI = new AtomicBoolean();
        cr.accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                String className = toClassName(name);
                isAPI.set(belongsToApi(className) && isPublicAPI(access) && !AIC_LOCAL_CLASS_PATTERN.matcher(name).matches());
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
        return isAPI.get();
    }

    /**
     * Strips out all the non public elements of a class and generates a new class out of it, based
     * on the list of allowed packages.
     * @param clazz the bytecode of a class
     * @return bytecode for the same class, stripped out of all non public members
     */
    public byte[] convertToApi(byte[] clazz) {
        ClassReader cr = new ClassReader(clazz);
        return convertToApi(cr);
    }

    public byte[] convertToApi(InputStream inputStream) throws IOException {
        try {
            ClassReader cr = new ClassReader(inputStream);
            return convertToApi(cr);
        } finally {
            inputStream.close();
        }
    }

    private byte[] convertToApi(ClassReader cr) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cr.accept(new PublicAPIAdapter(cw), ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return cw.toByteArray();
    }

    private static String toClassName(String cn) {
        return cn.replace('/', '.');
    }

    private static String extractPackageName(String className) {
        return className.indexOf(".") > 0 ? className.substring(0, className.lastIndexOf(".")) : "";
    }

    private boolean validateType(String className) {
        return !validateExposedTypes || belongsToApi(className);
    }

    private boolean belongsToApi(String className) {
        if (!hasDeclaredAPI) {
            return true;
        }

        String pkg = extractPackageName(className);

        for (String javaBasePackage : JavaBaseModule.PACKAGES) {
            if (pkg.equals(javaBasePackage)) {
                return true;
            }
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

    private boolean isProtected(int access) {
        return (access & Opcodes.ACC_PROTECTED) == Opcodes.ACC_PROTECTED;
    }

    private boolean isPublic(int access) {
        return (access & Opcodes.ACC_PUBLIC) == Opcodes.ACC_PUBLIC;
    }

    private boolean isPublicAPI(int access) {
        return (isPackagePrivate(access) && !hasDeclaredAPI) || isPublic(access) || isProtected(access);
    }

    private boolean isPackagePrivate(int access) {
        return access == 0
            || access == Opcodes.ACC_STATIC
            || access == Opcodes.ACC_SUPER
            || access == (Opcodes.ACC_SUPER | Opcodes.ACC_STATIC);
    }

    private class PublicAPIAdapter extends ClassVisitor implements Opcodes {

        public static final String UOE_METHOD = "$unsupportedOpEx";
        private String internalClassName;
        private boolean isInnerClass;

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
            internalClassName = name;
            isInnerClass = (access & ACC_SUPER) == ACC_SUPER;
            validateSuperTypes(name, signature, superName, interfaces);
            if ((access & ACC_INTERFACE) == 0) {
                generateUnsupportedOperationExceptionMethod();
            }
        }

        private void validateSuperTypes(String name, String signature, String superName, String[] interfaces) {
            if (!validateType(toClassName(superName))) {
                throw new InvalidPublicAPIException(String.format("'%s' extends '%s' and its package is not one of the allowed packages.", toClassName(name), toClassName(superName)));
            }
            Set<String> invalidReferencedTypes = invalidReferencedTypes(signature);
            if (!invalidReferencedTypes.isEmpty()) {
                if (invalidReferencedTypes.size() == 1) {
                    throw new InvalidPublicAPIException(String.format("'%s' references disallowed API type '%s' in superclass or interfaces.", toClassName(name), invalidReferencedTypes.iterator().next()));
                } else {
                    StringBuilder sb = new StringBuilder("The following types are referenced in ");
                    sb.append(toClassName(name));
                    sb.append(" superclass but their package don't belong to the allowed packages:\n");
                    for (String invalidReferencedType : invalidReferencedTypes) {
                        sb.append("   - ").append(invalidReferencedType).append("\n");
                    }
                    throw new InvalidPublicAPIException(sb.toString());
                }
            }
            if (interfaces != null) {
                for (String intf : interfaces) {
                    if (!validateType(toClassName(intf))) {
                        throw new InvalidPublicAPIException(String.format("'%s' declares interface '%s' and its package is not one of the allowed packages.", toClassName(name), toClassName(intf)));
                    }
                }
            }
        }

        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            checkAnnotation(toClassName(internalClassName), desc);
            return super.visitAnnotation(desc, visible);
        }

        private void checkAnnotation(String owner, String annotationDesc) {
            String annotation = Type.getType(annotationDesc).getClassName();
            if (!validateType(annotation)) {
                throw new InvalidPublicAPIException(String.format("'%s' is annotated with '%s' effectively exposing it in the public API but its package is not one of the allowed packages.", owner, annotation));
            }
        }

        @Override
        public MethodVisitor visitMethod(final int access, final String name, final String desc, final String signature, final String[] exceptions) {
            if ("<clinit>".equals(name)) {
                // discard static initializers
                return null;
            }
            if (isPublicAPI(access) || ("<init>".equals(name) && isInnerClass)) {
                Set<String> invalidReferencedTypes = invalidReferencedTypes(signature == null ? desc : signature);
                if (invalidReferencedTypes.isEmpty()) {
                    MethodVisitor mv = createAnnotationVisitor(access, name, desc, signature, exceptions, true);
                    if ((access & ACC_ABSTRACT) != ACC_ABSTRACT) {
                        mv.visitCode();
                        mv.visitMethodInsn(INVOKESTATIC, internalClassName, UOE_METHOD, "()Ljava/lang/UnsupportedOperationException;", false);
                        mv.visitInsn(ATHROW);
                        mv.visitMaxs(1, 0);
                        mv.visitEnd();
                        return createAnnotationVisitor(access, name, desc, signature, exceptions, false);
                    }
                    return mv;
                } else {
                    String methodDesc = prettifyMethodDescriptor(access, name, desc);
                    if (invalidReferencedTypes.size() == 1) {
                        throw new InvalidPublicAPIException(String.format("In %s, type %s is exposed in the public API but its package is not one of the allowed packages.", methodDesc, invalidReferencedTypes.iterator().next()));
                    } else {
                        StringBuilder sb = new StringBuilder("The following types are referenced in ");
                        sb.append(methodDesc);
                        sb.append(" but their package is not one of the allowed packages:\n");
                        for (String invalidReferencedType : invalidReferencedTypes) {
                            sb.append("   - ").append(invalidReferencedType).append("\n");
                        }
                        throw new InvalidPublicAPIException(sb.toString());
                    }
                }
            }
            return null;
        }

        private MethodVisitor createAnnotationVisitor(final int access, final String name, final String desc, final String signature, final String[] exceptions, boolean delegate) {
            return new MethodVisitor(Opcodes.ASM5, delegate?cv.visitMethod(access, name, desc, signature, exceptions):null) {
                @Override
                public AnnotationVisitor visitAnnotation(String annDesc, boolean visible) {
                    checkAnnotation(prettifyMethodDescriptor(access, name, desc), annDesc);
                    return super.visitAnnotation(desc, visible);
                }

                @Override
                public AnnotationVisitor visitParameterAnnotation(int parameter, String annDesc, boolean visible) {
                    checkAnnotation(prettifyMethodDescriptor(access, name, desc), annDesc);
                    return super.visitParameterAnnotation(parameter, desc, visible);
                }
            };
        }

        private String prettifyMethodDescriptor(int access, String name, String desc) {
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
            return methodDesc.toString();
        }

        private String prettifyFieldDescriptor(int access, String name, String desc) {
            return String.format("%s %s %s", Modifier.toString(access), Type.getType(desc).getClassName(), name);
        }

        private Set<String> invalidReferencedTypes(String signature) {
            if (signature==null) {
                return Collections.emptySet();
            }
            if (!validateExposedTypes || !hasDeclaredAPI) {
                return Collections.emptySet();
            }
            SignatureReader sr = new SignatureReader(signature);
            final Set<String> result = Sets.newLinkedHashSet();
            sr.accept(new SignatureVisitor(Opcodes.ASM5) {
                @Override
                public void visitClassType(String name) {
                    super.visitClassType(name);
                    String className = toClassName(name);
                    if (!validateType(className)) {
                        result.add(className);
                    }
                }
            });
            return result;
        }

        @Override
        public FieldVisitor visitField(final int access, final String name, final String desc, final String signature, Object value) {
            if (isPublicAPI(access)) {
                final String fieldDescriptor = prettifyFieldDescriptor(access, name, desc);
                Set<String> invalidReferencedTypes = invalidReferencedTypes(signature);
                if (!invalidReferencedTypes.isEmpty()) {
                    if (invalidReferencedTypes.size() == 1) {
                        throw new InvalidPublicAPIException(String.format("Field '%s' references disallowed API type '%s'", fieldDescriptor, invalidReferencedTypes.iterator().next()));
                    } else {
                        StringBuilder sb = new StringBuilder("The following types are referenced in ");
                        sb.append(fieldDescriptor);
                        sb.append(" but their package is not one of the allowed packages:\n");
                        for (String invalidReferencedType : invalidReferencedTypes) {
                            sb.append("   - ").append(invalidReferencedType).append("\n");
                        }
                        throw new InvalidPublicAPIException(sb.toString());
                    }
                }
                return new FieldVisitor(Opcodes.ASM5, cv.visitField(access, name, desc, signature, value)) {
                    @Override
                    public AnnotationVisitor visitAnnotation(String annotationDesc, boolean visible) {
                        checkAnnotation(fieldDescriptor, annotationDesc);
                        return super.visitAnnotation(annotationDesc, visible);
                    }
                };
            }
            return null;
        }

        @Override
        public void visitInnerClass(String name, String outerName, String innerName, int access) {
            if (isPackagePrivate(access) && hasDeclaredAPI) {
                return;
            }
            super.visitInnerClass(name, outerName, innerName, access);
        }
    }
}
