/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.classpath;

import groovy.lang.GroovyObject;
import org.codehaus.groovy.runtime.callsite.CallSiteArray;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Iterator;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

public class DecoratingTransformer {
    private static final Type SYSTEM_TYPE = Type.getType(System.class);
    private static final Type STRING_TYPE = Type.getType(String.class);
    private static final Type INSTRUMENTED_TYPE = Type.getType(Instrumented.class);
    private static final Type GROOVY_TYPE = Type.getType(GroovyObject.class);

    private static final String RETURN_STRING_FROM_STRING_STRING = Type.getMethodDescriptor(STRING_TYPE, STRING_TYPE, STRING_TYPE);
    private static final String RETURN_CALL_SITE_ARRAY = Type.getMethodDescriptor(Type.getType(CallSiteArray.class));
    private static final String RETURN_VOID_FROM_CALL_SITE_ARRAY = Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(CallSiteArray.class));

    private static final String INSTRUMENTED_CALL_SITE_METHOD = "$instrumentedCallSiteArray";
    private static final String CALL_SITE_ARRAY_METHOD = "$createCallSiteArray";

    private static final String[] NO_EXCEPTIONS = new String[0];
    private static final Attributes.Name DIGEST_ATTRIBUTE = new Attributes.Name("SHA1-Digest");

    private final ClasspathWalker classpathWalker;
    private final ClasspathBuilder classpathBuilder;

    public DecoratingTransformer(ClasspathWalker classpathWalker, ClasspathBuilder classpathBuilder) {
        this.classpathWalker = classpathWalker;
        this.classpathBuilder = classpathBuilder;
    }

    void transform(File source, File dest) {
        classpathBuilder.jar(dest, builder -> classpathWalker.visit(source, entry -> {
            if (entry.getName().endsWith(".class")) {
                ClassReader reader = new ClassReader(entry.getContent());
                ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                reader.accept(new InstrumentingVisitor(classWriter), 0);
                byte[] bytes = classWriter.toByteArray();
                builder.put(entry.getName(), bytes);
            } else if (entry.getName().equals("META-INF/MANIFEST.MF")) {
                // Remove the signature from the manifest, as the classes may have been instrumented
                Manifest manifest = new Manifest(new ByteArrayInputStream(entry.getContent()));
                manifest.getMainAttributes().remove(Attributes.Name.SIGNATURE_VERSION);
                Iterator<Map.Entry<String, Attributes>> entries = manifest.getEntries().entrySet().iterator();
                while (entries.hasNext()) {
                    Map.Entry<String, Attributes> manifestEntry = entries.next();
                    Attributes attributes = manifestEntry.getValue();
                    attributes.remove(DIGEST_ATTRIBUTE);
                    if (attributes.isEmpty()) {
                        entries.remove();
                    }
                }
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                manifest.write(outputStream);
                builder.put(entry.getName(), outputStream.toByteArray());
            } else if (!entry.getName().startsWith("META-INF/") || !entry.getName().endsWith(".SF")) {
                // Discard signature files, as the classes may have been instrumented
                // Else, copy resource
                builder.put(entry.getName(), entry.getContent());
            }
        }));
    }

    private static class InstrumentingVisitor extends ClassVisitor {
        private String className;
        private boolean groovyType;
        private boolean hasCallSites;

        public InstrumentingVisitor(ClassVisitor visitor) {
            super(Opcodes.ASM7, visitor);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            super.visit(version, access, name, signature, superName, interfaces);

            for (String anInterface : interfaces) {
                if (anInterface.equals(GROOVY_TYPE.getInternalName())) {
                    this.groovyType = true;
                    break;
                }
            }

            this.className = name;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);
            if (name.equals(CALL_SITE_ARRAY_METHOD) && descriptor.equals(RETURN_CALL_SITE_ARRAY)) {
                hasCallSites = true;
            }
            return new InstrumentingMethodVisitor(className, groovyType, methodVisitor);
        }

        @Override
        public void visitEnd() {
            if (groovyType && hasCallSites) {
                MethodVisitor methodVisitor = super.visitMethod(Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC | Opcodes.ACC_PRIVATE, INSTRUMENTED_CALL_SITE_METHOD, RETURN_CALL_SITE_ARRAY, null, NO_EXCEPTIONS);
                methodVisitor.visitCode();
                methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, className, CALL_SITE_ARRAY_METHOD, RETURN_CALL_SITE_ARRAY, false);
                methodVisitor.visitInsn(Opcodes.DUP);
                methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, INSTRUMENTED_TYPE.getInternalName(), "groovyCallSites", RETURN_VOID_FROM_CALL_SITE_ARRAY, false);
                methodVisitor.visitInsn(Opcodes.ARETURN);
                methodVisitor.visitMaxs(2, 0);
                methodVisitor.visitEnd();
            }
            super.visitEnd();
        }
    }

    private static class InstrumentingMethodVisitor extends MethodVisitor {
        private final String className;
        private final boolean groovyType;

        public InstrumentingMethodVisitor(String className, boolean groovyType, MethodVisitor methodVisitor) {
            super(Opcodes.ASM7, methodVisitor);
            this.className = className;
            this.groovyType = groovyType;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            if (opcode == Opcodes.INVOKESTATIC && owner.equals(SYSTEM_TYPE.getInternalName()) && name.equals("getProperty") && descriptor.equals(Type.getMethodDescriptor(STRING_TYPE, STRING_TYPE))) {
                // TODO - load the class literal instead of class name
                visitLdcInsn(Type.getObjectType(className).getClassName());
                super.visitMethodInsn(Opcodes.INVOKESTATIC, INSTRUMENTED_TYPE.getInternalName(), "systemProperty", RETURN_STRING_FROM_STRING_STRING, false);
                return;
            }
            if (groovyType && opcode == Opcodes.INVOKESTATIC && owner.equals(className) && name.equals(CALL_SITE_ARRAY_METHOD) && descriptor.equals(RETURN_CALL_SITE_ARRAY)) {
                super.visitMethodInsn(Opcodes.INVOKESTATIC, className, INSTRUMENTED_CALL_SITE_METHOD, RETURN_CALL_SITE_ARRAY, false);
                return;
            }
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        }
    }
}
