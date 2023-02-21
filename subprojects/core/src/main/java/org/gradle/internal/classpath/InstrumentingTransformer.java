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

import kotlin.io.FilesKt;
import org.codehaus.groovy.runtime.ProcessGroovyMethods;
import org.codehaus.groovy.runtime.ResourceGroovyMethods;
import org.codehaus.groovy.runtime.callsite.CallSiteArray;
import org.codehaus.groovy.vmplugin.v8.IndyInterface;
import org.gradle.api.Action;
import org.gradle.api.Transformer;
import org.gradle.api.file.RelativePath;
import org.gradle.api.specs.Spec;
import org.gradle.internal.Pair;
import org.gradle.internal.hash.Hasher;
import org.gradle.model.internal.asm.MethodVisitorScope;
import org.gradle.process.CommandLineArgumentProvider;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.SerializedLambda;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import static org.gradle.internal.classanalysis.AsmConstants.ASM_LEVEL;
import static org.objectweb.asm.Opcodes.ACC_INTERFACE;
import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ACC_SYNTHETIC;
import static org.objectweb.asm.Opcodes.H_INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Type.INT_TYPE;
import static org.objectweb.asm.Type.getMethodDescriptor;
import static org.objectweb.asm.Type.getObjectType;
import static org.objectweb.asm.Type.getType;

class InstrumentingTransformer implements CachedClasspathTransformer.Transform {

    /**
     * Decoration format. Increment this when making changes.
     */
    private static final int DECORATION_FORMAT = 22;

    private static final Type SYSTEM_TYPE = getType(System.class);
    private static final Type STRING_TYPE = getType(String.class);
    private static final Type INTEGER_TYPE = getType(Integer.class);
    private static final Type INSTRUMENTED_TYPE = getType(Instrumented.class);
    private static final Type OBJECT_TYPE = getType(Object.class);
    private static final Type SERIALIZED_LAMBDA_TYPE = getType(SerializedLambda.class);
    private static final Type LONG_TYPE = getType(Long.class);
    private static final Type BOOLEAN_TYPE = getType(Boolean.class);
    public static final Type PROPERTIES_TYPE = getType(Properties.class);

    private static final String RETURN_STRING = getMethodDescriptor(STRING_TYPE);
    private static final String RETURN_STRING_FROM_STRING = getMethodDescriptor(STRING_TYPE, STRING_TYPE);
    private static final String RETURN_STRING_FROM_STRING_STRING = getMethodDescriptor(STRING_TYPE, STRING_TYPE, STRING_TYPE);
    private static final String RETURN_STRING_FROM_STRING_STRING_STRING = getMethodDescriptor(STRING_TYPE, STRING_TYPE, STRING_TYPE, STRING_TYPE);
    private static final String RETURN_INTEGER_FROM_STRING = getMethodDescriptor(INTEGER_TYPE, STRING_TYPE);
    private static final String RETURN_INTEGER_FROM_STRING_INT = getMethodDescriptor(INTEGER_TYPE, STRING_TYPE, Type.INT_TYPE);
    private static final String RETURN_INTEGER_FROM_STRING_INTEGER = getMethodDescriptor(INTEGER_TYPE, STRING_TYPE, INTEGER_TYPE);
    private static final String RETURN_INTEGER_FROM_STRING_STRING = getMethodDescriptor(INTEGER_TYPE, STRING_TYPE, STRING_TYPE);
    private static final String RETURN_INTEGER_FROM_STRING_INT_STRING = getMethodDescriptor(INTEGER_TYPE, STRING_TYPE, Type.INT_TYPE, STRING_TYPE);
    private static final String RETURN_INTEGER_FROM_STRING_INTEGER_STRING = getMethodDescriptor(INTEGER_TYPE, STRING_TYPE, INTEGER_TYPE, STRING_TYPE);
    private static final String RETURN_LONG_FROM_STRING = getMethodDescriptor(LONG_TYPE, STRING_TYPE);
    private static final String RETURN_LONG_FROM_STRING_PRIMITIVE_LONG = getMethodDescriptor(LONG_TYPE, STRING_TYPE, Type.LONG_TYPE);
    private static final String RETURN_LONG_FROM_STRING_LONG = getMethodDescriptor(LONG_TYPE, STRING_TYPE, LONG_TYPE);
    private static final String RETURN_LONG_FROM_STRING_STRING = getMethodDescriptor(LONG_TYPE, STRING_TYPE, STRING_TYPE);
    private static final String RETURN_LONG_FROM_STRING_PRIMITIVE_LONG_STRING = getMethodDescriptor(LONG_TYPE, STRING_TYPE, Type.LONG_TYPE, STRING_TYPE);
    private static final String RETURN_LONG_FROM_STRING_LONG_STRING = getMethodDescriptor(LONG_TYPE, STRING_TYPE, LONG_TYPE, STRING_TYPE);
    private static final String RETURN_PRIMITIVE_BOOLEAN_FROM_STRING = getMethodDescriptor(Type.BOOLEAN_TYPE, STRING_TYPE);
    private static final String RETURN_PRIMITIVE_BOOLEAN_FROM_STRING_STRING = getMethodDescriptor(Type.BOOLEAN_TYPE, STRING_TYPE, STRING_TYPE);
    private static final String RETURN_OBJECT_FROM_INT = getMethodDescriptor(OBJECT_TYPE, Type.INT_TYPE);
    private static final String RETURN_BOOLEAN = getMethodDescriptor(Type.BOOLEAN_TYPE);
    private static final String RETURN_BOOLEAN_FROM_OBJECT = getMethodDescriptor(Type.BOOLEAN_TYPE, OBJECT_TYPE);
    private static final String RETURN_PROPERTIES = getMethodDescriptor(PROPERTIES_TYPE);
    private static final String RETURN_PROPERTIES_FROM_STRING = getMethodDescriptor(PROPERTIES_TYPE, STRING_TYPE);
    private static final String RETURN_VOID_FROM_PROPERTIES = getMethodDescriptor(Type.VOID_TYPE, PROPERTIES_TYPE);
    private static final String RETURN_VOID_FROM_PROPERTIES_STRING = getMethodDescriptor(Type.VOID_TYPE, PROPERTIES_TYPE, STRING_TYPE);
    private static final String RETURN_CALL_SITE_ARRAY = getMethodDescriptor(getType(CallSiteArray.class));
    private static final String RETURN_VOID_FROM_CALL_SITE_ARRAY = getMethodDescriptor(Type.VOID_TYPE, getType(CallSiteArray.class));
    private static final String RETURN_OBJECT_FROM_SERIALIZED_LAMBDA = getMethodDescriptor(OBJECT_TYPE, SERIALIZED_LAMBDA_TYPE);
    private static final String RETURN_MAP = getMethodDescriptor(getType(Map.class));
    private static final String RETURN_MAP_FROM_STRING = getMethodDescriptor(getType(Map.class), STRING_TYPE);

    private static final Type PROCESS_TYPE = getType(Process.class);
    private static final Type PROCESS_BUILDER_TYPE = getType(ProcessBuilder.class);
    private static final Type RUNTIME_TYPE = getType(Runtime.class);
    private static final Type PROCESS_GROOVY_METHODS_TYPE = getType(ProcessGroovyMethods.class);
    private static final Type STRING_ARRAY_TYPE = getType(String[].class);
    private static final Type FILE_TYPE = getType(File.class);
    private static final Type FILE_ARRAY_TYPE = getType(File[].class);
    private static final Type PATH_TYPE = getType(Path.class);
    private static final Type CHARSET_TYPE = getType(Charset.class);
    private static final Type LIST_TYPE = getType(List.class);

    private static final Type KOTLIN_IO_FILES_TYPE = getType(FilesKt.class);
    // readText(File, Charset) -> kotlinIoFilesKtReadText(File, Charset, String)
    private static final String RETURN_STRING_FROM_FILE_CHARSET = getMethodDescriptor(STRING_TYPE, FILE_TYPE, getType(Charset.class));
    private static final String RETURN_STRING_FROM_FILE_CHARSET_STRING = getMethodDescriptor(STRING_TYPE, FILE_TYPE, getType(Charset.class), STRING_TYPE);
    // readText$default(File, Charset, int, Object) -> kotlinIoFilesKtReadText(File, Charset, String)
    private static final String RETURN_STRING_FROM_FILE_CHARSET_INT_OBJECT = getMethodDescriptor(STRING_TYPE, FILE_TYPE, getType(Charset.class), INT_TYPE, OBJECT_TYPE);
    private static final String RETURN_STRING_FROM_FILE_CHARSET_INT_OBJECT_STRING = getMethodDescriptor(STRING_TYPE, FILE_TYPE, getType(Charset.class), INT_TYPE, OBJECT_TYPE, STRING_TYPE);

    private static final Type RESOURCE_GROOVY_METHODS_TYPE = getType(ResourceGroovyMethods.class);
    // file.text -(Groovy compiler)-> ResourceGroovyMethods.getText(File) -> groovyFileGetText(File, String)
    private static final String RETURN_STRING_FROM_FILE = getMethodDescriptor(STRING_TYPE, FILE_TYPE);
    private static final String RETURN_STRING_FROM_FILE_STRING = getMethodDescriptor(STRING_TYPE, FILE_TYPE, STRING_TYPE);
    // file.getText(String charset) -(Groovy compiler)-> ResourceGroovyMethods.getText(File, String) -> groovyFileGetText(File, String, String)
    private static final String RETURN_STRING_FROM_FILE_STRING_STRING = getMethodDescriptor(STRING_TYPE, FILE_TYPE, STRING_TYPE, STRING_TYPE);

    private static final Type FILES_TYPE = getType(Files.class);
    // readString(Path) -> filesReadString(Path, String)
    private static final String RETURN_STRING_FROM_PATH = getMethodDescriptor(STRING_TYPE, PATH_TYPE);
    private static final String RETURN_STRING_FROM_PATH_STRING = getMethodDescriptor(STRING_TYPE, PATH_TYPE, STRING_TYPE);
    // readString(Path, Charset) -> filesReadString(Path, Charset, String)
    private static final String RETURN_STRING_FROM_PATH_CHARSET = getMethodDescriptor(STRING_TYPE, PATH_TYPE, CHARSET_TYPE);
    private static final String RETURN_STRING_FROM_PATH_CHARSET_STRING = getMethodDescriptor(STRING_TYPE, PATH_TYPE, CHARSET_TYPE, STRING_TYPE);

    // ProcessBuilder().start() -> start(ProcessBuilder, String)
    private static final String RETURN_PROCESS = getMethodDescriptor(PROCESS_TYPE);
    private static final String RETURN_PROCESS_FROM_PROCESS_BUILDER_STRING = getMethodDescriptor(PROCESS_TYPE, PROCESS_BUILDER_TYPE, STRING_TYPE);
    // ProcessBuilder.startPipeline(List) -> startPipeline(List, String)
    private static final String RETURN_LIST_FROM_LIST = getMethodDescriptor(LIST_TYPE, LIST_TYPE);
    private static final String RETURN_LIST_FROM_LIST_STRING = getMethodDescriptor(LIST_TYPE, LIST_TYPE, STRING_TYPE);

    // Runtime().exec(String) -> exec(Runtime, String, String)
    // ProcessGroovyMethods.execute(String) -> execute(String, String)
    private static final String RETURN_PROCESS_FROM_STRING = getMethodDescriptor(PROCESS_TYPE, STRING_TYPE);
    private static final String RETURN_PROCESS_FROM_RUNTIME_STRING_STRING = getMethodDescriptor(PROCESS_TYPE, RUNTIME_TYPE, STRING_TYPE, STRING_TYPE);
    private static final String RETURN_PROCESS_FROM_STRING_STRING = getMethodDescriptor(PROCESS_TYPE, STRING_TYPE, STRING_TYPE);
    // Runtime().exec(String[]) -> exec(Runtime, String[], String)
    // ProcessGroovyMethods.execute(String[]) -> execute(String[], String)
    private static final String RETURN_PROCESS_FROM_STRING_ARRAY = getMethodDescriptor(PROCESS_TYPE, STRING_ARRAY_TYPE);
    private static final String RETURN_PROCESS_FROM_RUNTIME_STRING_ARRAY_STRING = getMethodDescriptor(PROCESS_TYPE, RUNTIME_TYPE, STRING_ARRAY_TYPE, STRING_TYPE);
    private static final String RETURN_PROCESS_FROM_STRING_ARRAY_STRING = getMethodDescriptor(PROCESS_TYPE, STRING_ARRAY_TYPE, STRING_TYPE);
    // ProcessGroovyMethods.execute(List) -> execute(List, String)
    private static final String RETURN_PROCESS_FROM_LIST = getMethodDescriptor(PROCESS_TYPE, LIST_TYPE);
    private static final String RETURN_PROCESS_FROM_LIST_STRING = getMethodDescriptor(PROCESS_TYPE, LIST_TYPE, STRING_TYPE);
    // Runtime().exec(String, String[]) -> exec(Runtume, String, String[], String)
    private static final String RETURN_PROCESS_FROM_STRING_STRING_ARRAY = getMethodDescriptor(PROCESS_TYPE, STRING_TYPE, STRING_ARRAY_TYPE);
    private static final String RETURN_PROCESS_FROM_RUNTIME_STRING_STRING_ARRAY_STRING = getMethodDescriptor(PROCESS_TYPE, RUNTIME_TYPE, STRING_TYPE, STRING_ARRAY_TYPE, STRING_TYPE);
    // Runtime().exec(String[], String[]) -> exec(Runtume, String[], String[], String)
    private static final String RETURN_PROCESS_FROM_STRING_ARRAY_STRING_ARRAY = getMethodDescriptor(PROCESS_TYPE, STRING_ARRAY_TYPE, STRING_ARRAY_TYPE);
    private static final String RETURN_PROCESS_FROM_RUNTIME_STRING_ARRAY_STRING_ARRAY_STRING = getMethodDescriptor(PROCESS_TYPE, RUNTIME_TYPE, STRING_ARRAY_TYPE, STRING_ARRAY_TYPE, STRING_TYPE);
    // Runtime().exec(String, String[], File) -> exec(Runtime, String, String[], File, String)
    // ProcessGroovyMethods.execute(String, String[], File) -> execute(String, String[], File, String)
    private static final String RETURN_PROCESS_FROM_STRING_STRING_ARRAY_FILE = getMethodDescriptor(PROCESS_TYPE, STRING_TYPE, STRING_ARRAY_TYPE, FILE_TYPE);
    private static final String RETURN_PROCESS_FROM_RUNTIME_STRING_STRING_ARRAY_FILE_STRING = getMethodDescriptor(PROCESS_TYPE, RUNTIME_TYPE, STRING_TYPE, STRING_ARRAY_TYPE, FILE_TYPE, STRING_TYPE);
    private static final String RETURN_PROCESS_FROM_STRING_STRING_ARRAY_FILE_STRING = getMethodDescriptor(PROCESS_TYPE, STRING_TYPE, STRING_ARRAY_TYPE, FILE_TYPE, STRING_TYPE);
    // Runtime().exec(String[], String[], File) -> exec(Runtime, String[], String[], File, String)
    // ProcessGroovyMethods.execute(String[], String[], File) -> execute(String[], String[], File, String)
    private static final String RETURN_PROCESS_FROM_STRING_ARRAY_STRING_ARRAY_FILE = getMethodDescriptor(PROCESS_TYPE, STRING_ARRAY_TYPE, STRING_ARRAY_TYPE, FILE_TYPE);
    private static final String RETURN_PROCESS_FROM_RUNTIME_STRING_ARRAY_STRING_ARRAY_FILE_STRING = getMethodDescriptor(PROCESS_TYPE, RUNTIME_TYPE, STRING_ARRAY_TYPE, STRING_ARRAY_TYPE, FILE_TYPE, STRING_TYPE);
    private static final String RETURN_PROCESS_FROM_STRING_ARRAY_STRING_ARRAY_FILE_STRING = getMethodDescriptor(PROCESS_TYPE, STRING_ARRAY_TYPE, STRING_ARRAY_TYPE, FILE_TYPE, STRING_TYPE);
    // ProcessGroovyMethods.execute(List, String[], File) -> execute(List, String[], File, String)
    private static final String RETURN_PROCESS_FROM_LIST_STRING_ARRAY_FILE = getMethodDescriptor(PROCESS_TYPE, LIST_TYPE, STRING_ARRAY_TYPE, FILE_TYPE);
    private static final String RETURN_PROCESS_FROM_LIST_STRING_ARRAY_FILE_STRING = getMethodDescriptor(PROCESS_TYPE, LIST_TYPE, STRING_ARRAY_TYPE, FILE_TYPE, STRING_TYPE);
    // ProcessGroovyMethods.execute(String, List, File) -> execute(String, List, File, String)
    private static final String RETURN_PROCESS_FROM_STRING_LIST_FILE = getMethodDescriptor(PROCESS_TYPE, STRING_TYPE, LIST_TYPE, FILE_TYPE);
    private static final String RETURN_PROCESS_FROM_STRING_LIST_FILE_STRING = getMethodDescriptor(PROCESS_TYPE, STRING_TYPE, LIST_TYPE, FILE_TYPE, STRING_TYPE);
    // ProcessGroovyMethods.execute(String[], List, File) -> execute(String[], List, File, String)
    private static final String RETURN_PROCESS_FROM_STRING_ARRAY_LIST_FILE = getMethodDescriptor(PROCESS_TYPE, STRING_ARRAY_TYPE, LIST_TYPE, FILE_TYPE);
    private static final String RETURN_PROCESS_FROM_STRING_ARRAY_LIST_FILE_STRING = getMethodDescriptor(PROCESS_TYPE, STRING_ARRAY_TYPE, LIST_TYPE, FILE_TYPE, STRING_TYPE);
    // ProcessGroovyMethods.execute(List, List, File) -> execute(List, List, File, String)
    private static final String RETURN_PROCESS_FROM_LIST_LIST_FILE = getMethodDescriptor(PROCESS_TYPE, LIST_TYPE, LIST_TYPE, FILE_TYPE);
    private static final String RETURN_PROCESS_FROM_LIST_LIST_FILE_STRING = getMethodDescriptor(PROCESS_TYPE, LIST_TYPE, LIST_TYPE, FILE_TYPE, STRING_TYPE);

    private static final Type FILE_INPUT_STREAM_TYPE = getType(FileInputStream.class);
    // FileInputStream(File) -> fileOpened(File, String)
    private static final String RETURN_VOID_FROM_FILE = getMethodDescriptor(Type.VOID_TYPE, FILE_TYPE);
    private static final String RETURN_VOID_FROM_FILE_STRING = getMethodDescriptor(Type.VOID_TYPE, FILE_TYPE, STRING_TYPE);
    // FileInputStream(String) -> fileOpened(String, String)
    private static final String RETURN_VOID_FROM_STRING = getMethodDescriptor(Type.VOID_TYPE, STRING_TYPE);
    private static final String RETURN_VOID_FROM_STRING_STRING = getMethodDescriptor(Type.VOID_TYPE, STRING_TYPE, STRING_TYPE);
    // File.exists() -> fileExists(File, String)
    private static final String RETURN_BOOLEAN_FROM_FILE_STRING = getMethodDescriptor(Type.BOOLEAN_TYPE, FILE_TYPE, STRING_TYPE);
    // File.listFiles() -> fileListFiles(File, String)
    private static final String RETURN_FILE_ARRAY = getMethodDescriptor(FILE_ARRAY_TYPE);
    private static final String RETURN_FILE_ARRAY_FROM_FILE_STRING = getMethodDescriptor(FILE_ARRAY_TYPE, FILE_TYPE, STRING_TYPE);
    // File.listFiles(FileFilter) -> fileListFiles(File, FileFilter, String)
    private static final String RETURN_FILE_ARRAY_FROM_FILEFILTER = getMethodDescriptor(FILE_ARRAY_TYPE, getType(FileFilter.class));
    private static final String RETURN_FILE_ARRAY_FROM_FILE_FILEFILTER_STRING = getMethodDescriptor(FILE_ARRAY_TYPE, FILE_TYPE, getType(FileFilter.class), STRING_TYPE);
    // File.listFiles(FileFilter) -> fileListFiles(File, FileFilter, String)
    private static final String RETURN_FILE_ARRAY_FROM_FILENAMEFILTER = getMethodDescriptor(FILE_ARRAY_TYPE, getType(FilenameFilter.class));
    private static final String RETURN_FILE_ARRAY_FROM_FILE_FILENAMEFILTER_STRING = getMethodDescriptor(FILE_ARRAY_TYPE, FILE_TYPE, getType(FilenameFilter.class), STRING_TYPE);

    private static final String LAMBDA_METAFACTORY_TYPE = getType(LambdaMetafactory.class).getInternalName();
    private static final String LAMBDA_METAFACTORY_METHOD_DESCRIPTOR = getMethodDescriptor(getType(CallSite.class), getType(MethodHandles.Lookup.class), STRING_TYPE, getType(MethodType.class), getType(Object[].class));

    private static final String GROOVY_INDY_INTERFACE_TYPE = getType(IndyInterface.class).getInternalName();

    @SuppressWarnings("deprecation")
    private static final String GROOVY_INDY_INTERFACE_V7_TYPE = getType(org.codehaus.groovy.vmplugin.v7.IndyInterface.class).getInternalName();
    private static final String GROOVY_INDY_INTERFACE_BOOTSTRAP_METHOD_DESCRIPTOR = getMethodDescriptor(getType(CallSite.class), getType(MethodHandles.Lookup.class), STRING_TYPE, getType(MethodType.class), STRING_TYPE, INT_TYPE);

    private static final String INSTRUMENTED_CALL_SITE_METHOD = "$instrumentedCallSiteArray";
    private static final String CREATE_CALL_SITE_ARRAY_METHOD = "$createCallSiteArray";
    private static final String DESERIALIZE_LAMBDA = "$deserializeLambda$";
    private static final String RENAMED_DESERIALIZE_LAMBDA = "$renamedDeserializeLambda$";

    private static final String[] NO_EXCEPTIONS = new String[0];

    @Override
    public void applyConfigurationTo(Hasher hasher) {
        hasher.putString(InstrumentingTransformer.class.getSimpleName());
        hasher.putInt(DECORATION_FORMAT);
    }

    @Override
    public Pair<RelativePath, ClassVisitor> apply(ClasspathEntryVisitor.Entry entry, ClassVisitor visitor) {
        return Pair.of(entry.getPath(), new InstrumentingVisitor(new InstrumentingBackwardsCompatibilityVisitor(visitor)));
    }

    private static class InstrumentingVisitor extends ClassVisitor {
        String className;
        private final List<LambdaFactoryDetails> lambdaFactories = new ArrayList<>();
        private boolean hasGroovyCallSites;
        private boolean hasDeserializeLambda;
        private boolean isInterface;

        public InstrumentingVisitor(ClassVisitor visitor) {
            super(ASM_LEVEL, visitor);
        }

        public void addSerializedLambda(LambdaFactoryDetails lambdaFactoryDetails) {
            lambdaFactories.add(lambdaFactoryDetails);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            super.visit(version, access, name, signature, superName, interfaces);
            this.className = name;
            this.isInterface = (access & ACC_INTERFACE) != 0;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            if (name.equals(CREATE_CALL_SITE_ARRAY_METHOD) && descriptor.equals(RETURN_CALL_SITE_ARRAY)) {
                hasGroovyCallSites = true;
            } else if (name.equals(DESERIALIZE_LAMBDA) && descriptor.equals(RETURN_OBJECT_FROM_SERIALIZED_LAMBDA)) {
                hasDeserializeLambda = true;
                return super.visitMethod(access, RENAMED_DESERIALIZE_LAMBDA, descriptor, signature, exceptions);
            }
            MethodVisitor methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);
            return new InstrumentingMethodVisitor(this, methodVisitor);
        }

        @Override
        public void visitEnd() {
            if (hasGroovyCallSites) {
                generateCallSiteFactoryMethod();
            }
            if (!lambdaFactories.isEmpty() || hasDeserializeLambda) {
                generateLambdaDeserializeMethod();
            }
            super.visitEnd();
        }

        private void generateLambdaDeserializeMethod() {
            new MethodVisitorScope(visitStaticPrivateMethod(DESERIALIZE_LAMBDA, RETURN_OBJECT_FROM_SERIALIZED_LAMBDA)) {{
                Label next = null;
                for (LambdaFactoryDetails factory : lambdaFactories) {
                    if (next != null) {
                        visitLabel(next);
                        _F_SAME();
                    }
                    next = new Label();
                    Handle implHandle = (Handle) factory.bootstrapMethodArguments.get(1);

                    _ALOAD(0);
                    _INVOKEVIRTUAL(SERIALIZED_LAMBDA_TYPE, "getImplMethodName", RETURN_STRING);
                    _LDC(implHandle.getName());
                    _INVOKEVIRTUAL(OBJECT_TYPE, "equals", RETURN_BOOLEAN_FROM_OBJECT);
                    _IFEQ(next);

                    _ALOAD(0);
                    _INVOKEVIRTUAL(SERIALIZED_LAMBDA_TYPE, "getImplMethodSignature", RETURN_STRING);
                    _LDC(implHandle.getDesc());
                    _INVOKEVIRTUAL(OBJECT_TYPE, "equals", RETURN_BOOLEAN_FROM_OBJECT);
                    _IFEQ(next);

                    Type[] argumentTypes = Type.getArgumentTypes(factory.descriptor);
                    for (int i = 0; i < argumentTypes.length; i++) {
                        _ALOAD(0);
                        _LDC(i);
                        _INVOKEVIRTUAL(SERIALIZED_LAMBDA_TYPE, "getCapturedArg", RETURN_OBJECT_FROM_INT);
                        _UNBOX(argumentTypes[i]);
                    }
                    _INVOKEDYNAMIC(factory.name, factory.descriptor, factory.bootstrapMethodHandle, factory.bootstrapMethodArguments);
                    _ARETURN();
                }
                if (next != null) {
                    visitLabel(next);
                    _F_SAME();
                }
                if (hasDeserializeLambda) {
                    _ALOAD(0);
                    _INVOKESTATIC(className, RENAMED_DESERIALIZE_LAMBDA, RETURN_OBJECT_FROM_SERIALIZED_LAMBDA, isInterface);
                } else {
                    _ACONST_NULL();
                }
                _ARETURN();
                visitMaxs(0, 0);
                visitEnd();
            }};
        }

        private void generateCallSiteFactoryMethod() {
            new MethodVisitorScope(visitStaticPrivateMethod(INSTRUMENTED_CALL_SITE_METHOD, RETURN_CALL_SITE_ARRAY)) {{
                _INVOKESTATIC(className, CREATE_CALL_SITE_ARRAY_METHOD, RETURN_CALL_SITE_ARRAY);
                _DUP();
                _INVOKESTATIC(INSTRUMENTED_TYPE, "groovyCallSites", RETURN_VOID_FROM_CALL_SITE_ARRAY);
                _ARETURN();
                visitMaxs(2, 0);
                visitEnd();
            }};
        }

        private MethodVisitor visitStaticPrivateMethod(String name, String descriptor) {
            return super.visitMethod(ACC_STATIC | ACC_SYNTHETIC | ACC_PRIVATE, name, descriptor, null, NO_EXCEPTIONS);
        }
    }

    private static class InstrumentingMethodVisitor extends MethodVisitorScope {
        private final InstrumentingVisitor owner;
        private final String className;

        public InstrumentingMethodVisitor(InstrumentingVisitor owner, MethodVisitor methodVisitor) {
            super(methodVisitor);
            this.owner = owner;
            this.className = owner.className;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            if (opcode == INVOKESTATIC && visitINVOKESTATIC(owner, name, descriptor)) {
                return;
            }
            if (opcode == INVOKEVIRTUAL && visitINVOKEVIRTUAL(owner, name, descriptor)) {
                return;
            }
            if (opcode == INVOKESPECIAL && visitINVOKESPECIAL(owner, name, descriptor)) {
                return;
            }
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        }

        private boolean visitINVOKESTATIC(String owner, String name, String descriptor) {
            // TODO - load the class literal instead of class name to pass to the methods on Instrumented
            if (owner.equals(SYSTEM_TYPE.getInternalName())) {
                if (name.equals("getProperty")) {
                    if (descriptor.equals(RETURN_STRING_FROM_STRING)) {
                        _LDC(binaryClassNameOf(className));
                        _INVOKESTATIC(INSTRUMENTED_TYPE, "systemProperty", RETURN_STRING_FROM_STRING_STRING);
                        return true;
                    }
                    if (descriptor.equals(RETURN_STRING_FROM_STRING_STRING)) {
                        _LDC(binaryClassNameOf(className));
                        _INVOKESTATIC(INSTRUMENTED_TYPE, "systemProperty", RETURN_STRING_FROM_STRING_STRING_STRING);
                        return true;
                    }
                } else if (name.equals("getProperties") && descriptor.equals(RETURN_PROPERTIES)) {
                    _LDC(binaryClassNameOf(className));
                    _INVOKESTATIC(INSTRUMENTED_TYPE, "systemProperties", RETURN_PROPERTIES_FROM_STRING);
                    return true;
                } else if (name.equals("setProperties") && descriptor.equals(RETURN_VOID_FROM_PROPERTIES)) {
                    _LDC(binaryClassNameOf(className));
                    _INVOKESTATIC(INSTRUMENTED_TYPE, "setSystemProperties", RETURN_VOID_FROM_PROPERTIES_STRING);
                    return true;
                } else if (name.equals("setProperty") && descriptor.equals(RETURN_STRING_FROM_STRING_STRING)) {
                    _LDC(binaryClassNameOf(className));
                    _INVOKESTATIC(INSTRUMENTED_TYPE, "setSystemProperty", RETURN_STRING_FROM_STRING_STRING_STRING);
                    return true;
                } else if (name.equals("clearProperty") && descriptor.equals(RETURN_STRING_FROM_STRING)) {
                    _LDC(binaryClassNameOf(className));
                    _INVOKESTATIC(INSTRUMENTED_TYPE, "clearSystemProperty", RETURN_STRING_FROM_STRING_STRING);
                    return true;
                } else if (name.equals("getenv")) {
                    if (descriptor.equals(RETURN_STRING_FROM_STRING)) {
                        // System.getenv(String) -> String
                        _LDC(binaryClassNameOf(className));
                        _INVOKESTATIC(INSTRUMENTED_TYPE, "getenv", RETURN_STRING_FROM_STRING_STRING);
                        return true;
                    } else if (descriptor.equals(RETURN_MAP)) {
                        // System.getenv() -> Map<String, String>
                        _LDC(binaryClassNameOf(className));
                        _INVOKESTATIC(INSTRUMENTED_TYPE, "getenv", RETURN_MAP_FROM_STRING);
                        return true;
                    }
                }
            } else if (owner.equals(INTEGER_TYPE.getInternalName()) && name.equals("getInteger")) {
                if (descriptor.equals(RETURN_INTEGER_FROM_STRING)) {
                    _LDC(binaryClassNameOf(className));
                    _INVOKESTATIC(INSTRUMENTED_TYPE, "getInteger", RETURN_INTEGER_FROM_STRING_STRING);
                    return true;
                }
                if (descriptor.equals(RETURN_INTEGER_FROM_STRING_INT)) {
                    _LDC(binaryClassNameOf(className));
                    _INVOKESTATIC(INSTRUMENTED_TYPE, "getInteger", RETURN_INTEGER_FROM_STRING_INT_STRING);
                    return true;
                }
                if (descriptor.equals(RETURN_INTEGER_FROM_STRING_INTEGER)) {
                    _LDC(binaryClassNameOf(className));
                    _INVOKESTATIC(INSTRUMENTED_TYPE, "getInteger", RETURN_INTEGER_FROM_STRING_INTEGER_STRING);
                    return true;
                }
            } else if (owner.equals(LONG_TYPE.getInternalName()) && name.equals("getLong")) {
                if (descriptor.equals(RETURN_LONG_FROM_STRING)) {
                    _LDC(binaryClassNameOf(className));
                    _INVOKESTATIC(INSTRUMENTED_TYPE, "getLong", RETURN_LONG_FROM_STRING_STRING);
                    return true;
                }
                if (descriptor.equals(RETURN_LONG_FROM_STRING_PRIMITIVE_LONG)) {
                    _LDC(binaryClassNameOf(className));
                    _INVOKESTATIC(INSTRUMENTED_TYPE, "getLong", RETURN_LONG_FROM_STRING_PRIMITIVE_LONG_STRING);
                    return true;
                }
                if (descriptor.equals(RETURN_LONG_FROM_STRING_LONG)) {
                    _LDC(binaryClassNameOf(className));
                    _INVOKESTATIC(INSTRUMENTED_TYPE, "getLong", RETURN_LONG_FROM_STRING_LONG_STRING);
                    return true;
                }
            } else if (owner.equals(BOOLEAN_TYPE.getInternalName()) && name.equals("getBoolean") && descriptor.equals(RETURN_PRIMITIVE_BOOLEAN_FROM_STRING)) {
                _LDC(binaryClassNameOf(className));
                _INVOKESTATIC(INSTRUMENTED_TYPE, "getBoolean", RETURN_PRIMITIVE_BOOLEAN_FROM_STRING_STRING);
                return true;
            } else if (owner.equals(PROCESS_GROOVY_METHODS_TYPE.getInternalName()) && name.equals("execute")) {
                Optional<String> instrumentedDescriptor = getInstrumentedDescriptorForProcessGroovyMethodsExecuteDescriptor(descriptor);
                if (!instrumentedDescriptor.isPresent()) {
                    return false;
                }
                _LDC(binaryClassNameOf(className));
                _INVOKESTATIC(INSTRUMENTED_TYPE, "execute", instrumentedDescriptor.get());
                return true;
            } else if (owner.equals(KOTLIN_IO_FILES_TYPE.getInternalName())) {
                if (name.equals("readText") && descriptor.equals(RETURN_STRING_FROM_FILE_CHARSET)) {
                    _LDC(binaryClassNameOf(className));
                    _INVOKESTATIC(INSTRUMENTED_TYPE, "kotlinIoFilesKtReadText", RETURN_STRING_FROM_FILE_CHARSET_STRING);
                    return true;
                }
                if (name.equals("readText$default") && descriptor.equals(RETURN_STRING_FROM_FILE_CHARSET_INT_OBJECT)) {
                    _LDC(binaryClassNameOf(className));
                    _INVOKESTATIC(INSTRUMENTED_TYPE, "kotlinIoFilesKtReadTextDefault", RETURN_STRING_FROM_FILE_CHARSET_INT_OBJECT_STRING);
                    return true;
                }
            } else if (owner.equals(FILES_TYPE.getInternalName())) {
                if (name.equals("readString") && descriptor.equals(RETURN_STRING_FROM_PATH)) {
                    _LDC(binaryClassNameOf(className));
                    _INVOKESTATIC(INSTRUMENTED_TYPE, "filesReadString", RETURN_STRING_FROM_PATH_STRING);
                    return true;
                }
                if (name.equals("readString") && descriptor.equals(RETURN_STRING_FROM_PATH_CHARSET)) {
                    _LDC(binaryClassNameOf(className));
                    _INVOKESTATIC(INSTRUMENTED_TYPE, "filesReadString", RETURN_STRING_FROM_PATH_CHARSET_STRING);
                    return true;
                }
            } else if (owner.equals(RESOURCE_GROOVY_METHODS_TYPE.getInternalName())) {
                if (name.equals("getText") && descriptor.equals(RETURN_STRING_FROM_FILE)) {
                    _LDC(binaryClassNameOf(className));
                    _INVOKESTATIC(INSTRUMENTED_TYPE, "groovyFileGetText", RETURN_STRING_FROM_FILE_STRING);
                    return true;
                } else if (name.equals("getText") && descriptor.equals(RETURN_STRING_FROM_FILE_STRING)) {
                    _LDC(binaryClassNameOf(className));
                    _INVOKESTATIC(INSTRUMENTED_TYPE, "groovyFileGetText", RETURN_STRING_FROM_FILE_STRING_STRING);
                    return true;
                }
            }
            if (owner.equals(PROCESS_BUILDER_TYPE.getInternalName()) && name.equals("startPipeline") && descriptor.equals(RETURN_LIST_FROM_LIST)) {
                _LDC(binaryClassNameOf(className));
                _INVOKESTATIC(INSTRUMENTED_TYPE, "startPipeline", RETURN_LIST_FROM_LIST_STRING);
                return true;
            } else if (owner.equals(className) && name.equals(CREATE_CALL_SITE_ARRAY_METHOD) && descriptor.equals(RETURN_CALL_SITE_ARRAY)) {
                _INVOKESTATIC(className, INSTRUMENTED_CALL_SITE_METHOD, RETURN_CALL_SITE_ARRAY);
                return true;
            }
            return false;
        }

        private boolean visitINVOKEVIRTUAL(String owner, String name, String descriptor) {
            // Runtime.exec(...)
            if (owner.equals(RUNTIME_TYPE.getInternalName()) && name.equals("exec")) {
                Optional<String> instrumentedDescriptor = getInstrumentedDescriptorForRuntimeExecDescriptor(descriptor);
                if (!instrumentedDescriptor.isPresent()) {
                    return false;
                }
                _LDC(binaryClassNameOf(className));
                _INVOKESTATIC(INSTRUMENTED_TYPE, "exec", instrumentedDescriptor.get());
                return true;
            }
            if (owner.equals(PROCESS_BUILDER_TYPE.getInternalName())) {
                if (name.equals("start") && descriptor.equals(RETURN_PROCESS)) {
                    _LDC(binaryClassNameOf(className));
                    _INVOKESTATIC(INSTRUMENTED_TYPE, "start", RETURN_PROCESS_FROM_PROCESS_BUILDER_STRING);
                    return true;
                }
            }
            if (owner.equals(FILE_TYPE.getInternalName())) {
                if ((name.equals("exists") || name.equals("isFile") || name.equals("isDirectory"))
                    && descriptor.equals(RETURN_BOOLEAN)
                ) {
                    String instrumentedMethodName =
                        name.equals("exists") ? "fileExists" :
                            name.equals("isFile") ? "fileIsFile" :
                                "fileIsDirectory";
                    _LDC(binaryClassNameOf(className));
                    _INVOKESTATIC(INSTRUMENTED_TYPE, instrumentedMethodName, RETURN_BOOLEAN_FROM_FILE_STRING);
                    return true;
                }

                if (name.equals("listFiles") &&
                    (descriptor.equals(RETURN_FILE_ARRAY) ||
                        descriptor.equals(RETURN_FILE_ARRAY_FROM_FILEFILTER) ||
                        descriptor.equals(RETURN_FILE_ARRAY_FROM_FILENAMEFILTER))
                ) {
                    String instrumentedDescriptor =
                        descriptor.equals(RETURN_FILE_ARRAY) ? RETURN_FILE_ARRAY_FROM_FILE_STRING  :
                            descriptor.equals(RETURN_FILE_ARRAY_FROM_FILEFILTER) ? RETURN_FILE_ARRAY_FROM_FILE_FILEFILTER_STRING :
                                RETURN_FILE_ARRAY_FROM_FILE_FILENAMEFILTER_STRING;

                    _LDC(binaryClassNameOf(className));
                    _INVOKESTATIC(INSTRUMENTED_TYPE, "fileListFiles", instrumentedDescriptor);
                    return true;
                }
            }
            return false;
        }

        private Optional<String> getInstrumentedDescriptorForProcessGroovyMethodsExecuteDescriptor(String descriptor) {
            if (descriptor.equals(RETURN_PROCESS_FROM_STRING)) {
                // execute(String)
                return Optional.of(RETURN_PROCESS_FROM_STRING_STRING);
            } else if (descriptor.equals(RETURN_PROCESS_FROM_STRING_ARRAY)) {
                // execute(String[])
                return Optional.of(RETURN_PROCESS_FROM_STRING_ARRAY_STRING);
            } else if (descriptor.equals(RETURN_PROCESS_FROM_LIST)) {
                // execute(List)
                return Optional.of(RETURN_PROCESS_FROM_LIST_STRING);
            } else if (descriptor.equals(RETURN_PROCESS_FROM_STRING_STRING_ARRAY_FILE)) {
                // execute(String, String[], File)
                return Optional.of(RETURN_PROCESS_FROM_STRING_STRING_ARRAY_FILE_STRING);
            } else if (descriptor.equals(RETURN_PROCESS_FROM_STRING_ARRAY_STRING_ARRAY_FILE)) {
                // execute(String[], String[], File)
                return Optional.of(RETURN_PROCESS_FROM_STRING_ARRAY_STRING_ARRAY_FILE_STRING);
            } else if (descriptor.equals(RETURN_PROCESS_FROM_LIST_STRING_ARRAY_FILE)) {
                // execute(List, String[], File)
                return Optional.of(RETURN_PROCESS_FROM_LIST_STRING_ARRAY_FILE_STRING);
            } else if (descriptor.equals(RETURN_PROCESS_FROM_STRING_LIST_FILE)) {
                // execute(String, List, File)
                return Optional.of(RETURN_PROCESS_FROM_STRING_LIST_FILE_STRING);
            } else if (descriptor.equals(RETURN_PROCESS_FROM_STRING_ARRAY_LIST_FILE)) {
                // execute(String[], List, File)
                return Optional.of(RETURN_PROCESS_FROM_STRING_ARRAY_LIST_FILE_STRING);
            } else if (descriptor.equals(RETURN_PROCESS_FROM_LIST_LIST_FILE)) {
                // execute(List, List, File)
                return Optional.of(RETURN_PROCESS_FROM_LIST_LIST_FILE_STRING);
            }
            // It is some signature of ProcessGroovyMethods.execute that we don't know about.
            return Optional.empty();
        }

        private Optional<String> getInstrumentedDescriptorForRuntimeExecDescriptor(String descriptor) {
            if (descriptor.equals(RETURN_PROCESS_FROM_STRING)) {
                return Optional.of(RETURN_PROCESS_FROM_RUNTIME_STRING_STRING);
            } else if (descriptor.equals(RETURN_PROCESS_FROM_STRING_ARRAY)) {
                return Optional.of(RETURN_PROCESS_FROM_RUNTIME_STRING_ARRAY_STRING);
            } else if (descriptor.equals(RETURN_PROCESS_FROM_STRING_STRING_ARRAY)) {
                return Optional.of(RETURN_PROCESS_FROM_RUNTIME_STRING_STRING_ARRAY_STRING);
            } else if (descriptor.equals(RETURN_PROCESS_FROM_STRING_ARRAY_STRING_ARRAY)) {
                return Optional.of(RETURN_PROCESS_FROM_RUNTIME_STRING_ARRAY_STRING_ARRAY_STRING);
            } else if (descriptor.equals(RETURN_PROCESS_FROM_STRING_STRING_ARRAY_FILE)) {
                return Optional.of(RETURN_PROCESS_FROM_RUNTIME_STRING_STRING_ARRAY_FILE_STRING);
            } else if (descriptor.equals(RETURN_PROCESS_FROM_STRING_ARRAY_STRING_ARRAY_FILE)) {
                return Optional.of(RETURN_PROCESS_FROM_RUNTIME_STRING_ARRAY_STRING_ARRAY_FILE_STRING);
            }
            // It is some signature of Runtime.exec that we don't know about.
            return Optional.empty();
        }

        private boolean visitINVOKESPECIAL(String owner, String name, String descriptor) {
            if (owner.equals(FILE_INPUT_STREAM_TYPE.getInternalName()) && name.equals("<init>")) {
                Optional<String> instrumentedDescriptor = getInstrumentedDescriptorForFileInputStreamConstructor(descriptor);
                if (instrumentedDescriptor.isPresent()) {
                    // We are still calling the original constructor instead of replacing it with an instrumented method. The instrumented method is merely a notifier
                    // there.
                    _DUP();
                    _LDC(binaryClassNameOf(className));
                    _INVOKESTATIC(INSTRUMENTED_TYPE, "fileOpened", instrumentedDescriptor.get());
                    _INVOKESPECIAL(owner, name, descriptor);
                    return true;
                }
            }
            return false;
        }

        private Optional<String> getInstrumentedDescriptorForFileInputStreamConstructor(String descriptor) {
            if (descriptor.equals(RETURN_VOID_FROM_FILE)) {
                return Optional.of(RETURN_VOID_FROM_FILE_STRING);
            } else if (descriptor.equals(RETURN_VOID_FROM_STRING)) {
                return Optional.of(RETURN_VOID_FROM_STRING_STRING);
            }
            // It is some signature of FileInputStream.<init> that we don't support.
            return Optional.empty();
        }

        @Override
        public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
            if (bootstrapMethodHandle.getOwner().equals(LAMBDA_METAFACTORY_TYPE) && bootstrapMethodHandle.getName().equals("metafactory")) {
                Handle altMethod = new Handle(
                    H_INVOKESTATIC,
                    LAMBDA_METAFACTORY_TYPE,
                    "altMetafactory",
                    LAMBDA_METAFACTORY_METHOD_DESCRIPTOR,
                    false
                );
                List<Object> args = new ArrayList<>(bootstrapMethodArguments.length + 1);
                Collections.addAll(args, bootstrapMethodArguments);
                args.add(LambdaMetafactory.FLAG_SERIALIZABLE);
                super.visitInvokeDynamicInsn(name, descriptor, altMethod, args.toArray());
                owner.addSerializedLambda(new LambdaFactoryDetails(name, descriptor, altMethod, args));
            } else if (isGroovyIndyCallsite(descriptor, bootstrapMethodHandle)) {
                Handle interceptor = new Handle(
                    H_INVOKESTATIC,
                    INSTRUMENTED_TYPE.getInternalName(),
                    "bootstrap",
                    GROOVY_INDY_INTERFACE_BOOTSTRAP_METHOD_DESCRIPTOR,
                    false
                );
                super.visitInvokeDynamicInsn(name, descriptor, interceptor, bootstrapMethodArguments);
            } else {
                super.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);
            }
        }

        private boolean isGradleLambdaDescriptor(String descriptor) {
            return descriptor.endsWith(ACTION_LAMBDA_SUFFIX)
                || descriptor.endsWith(SPEC_LAMBDA_SUFFIX)
                || descriptor.endsWith(TRANSFORMER_LAMBDA_SUFFIX)
                || descriptor.endsWith(COMMAND_LINE_ARGUMENT_PROVIDER_LAMBDA_SUFFIX);
        }

        private String binaryClassNameOf(String className) {
            return getObjectType(className).getClassName();
        }

        private boolean isGroovyIndyCallsite(String descriptor, Handle bootstrapMethodHandle) {
            return (bootstrapMethodHandle.getOwner().equals(GROOVY_INDY_INTERFACE_TYPE) ||
                bootstrapMethodHandle.getOwner().equals(GROOVY_INDY_INTERFACE_V7_TYPE)) &&
                bootstrapMethodHandle.getName().equals("bootstrap") &&
                bootstrapMethodHandle.getDesc().equals(GROOVY_INDY_INTERFACE_BOOTSTRAP_METHOD_DESCRIPTOR);
        }

        private static final String ACTION_LAMBDA_SUFFIX = ")" + getType(Action.class).getDescriptor();
        private static final String SPEC_LAMBDA_SUFFIX = ")" + getType(Spec.class).getDescriptor();
        private static final String TRANSFORMER_LAMBDA_SUFFIX = ")" + getType(Transformer.class).getDescriptor();
        private static final String COMMAND_LINE_ARGUMENT_PROVIDER_LAMBDA_SUFFIX = ")" + getType(CommandLineArgumentProvider.class).getDescriptor();
    }

    private static class LambdaFactoryDetails {
        final String name;
        final String descriptor;
        final Handle bootstrapMethodHandle;
        final List<?> bootstrapMethodArguments;

        public LambdaFactoryDetails(String name, String descriptor, Handle bootstrapMethodHandle, List<?> bootstrapMethodArguments) {
            this.name = name;
            this.descriptor = descriptor;
            this.bootstrapMethodHandle = bootstrapMethodHandle;
            this.bootstrapMethodArguments = bootstrapMethodArguments;
        }
    }
}
