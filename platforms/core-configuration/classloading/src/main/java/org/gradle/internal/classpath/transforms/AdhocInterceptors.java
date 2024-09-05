/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.classpath.transforms;

import org.codehaus.groovy.runtime.ProcessGroovyMethods;
import org.gradle.internal.classpath.Instrumented;
import org.gradle.internal.instrumentation.api.jvmbytecode.BridgeMethodBuilder;
import org.gradle.internal.instrumentation.api.jvmbytecode.DefaultBridgeMethodBuilder;
import org.gradle.internal.instrumentation.api.jvmbytecode.JvmBytecodeCallInterceptor;
import org.gradle.internal.instrumentation.api.types.BytecodeInterceptorType;
import org.gradle.model.internal.asm.MethodVisitorScope;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;

import javax.annotation.Nullable;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Supplier;

import static org.gradle.internal.classpath.transforms.CommonTypes.STRING_TYPE;
import static org.objectweb.asm.Type.getMethodDescriptor;
import static org.objectweb.asm.Type.getObjectType;

/**
 * Handwritten bytecode interceptors to be migrated to the annotation processor infrastructure.
 */
public class AdhocInterceptors implements JvmBytecodeCallInterceptor {
    private static final Type SYSTEM_TYPE = Type.getType(System.class);
    private static final Type INTEGER_TYPE = Type.getType(Integer.class);
    private static final Type INSTRUMENTED_TYPE = Type.getType(Instrumented.class);
    private static final Type LONG_TYPE = Type.getType(Long.class);
    private static final Type BOOLEAN_TYPE = Type.getType(Boolean.class);
    private static final Type PROPERTIES_TYPE = Type.getType(Properties.class);

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
    private static final String RETURN_PROPERTIES = getMethodDescriptor(PROPERTIES_TYPE);
    private static final String RETURN_PROPERTIES_FROM_STRING = getMethodDescriptor(PROPERTIES_TYPE, STRING_TYPE);
    private static final String RETURN_VOID_FROM_PROPERTIES = getMethodDescriptor(Type.VOID_TYPE, PROPERTIES_TYPE);
    private static final String RETURN_VOID_FROM_PROPERTIES_STRING = getMethodDescriptor(Type.VOID_TYPE, PROPERTIES_TYPE, STRING_TYPE);
    private static final String RETURN_MAP = getMethodDescriptor(Type.getType(Map.class));
    private static final String RETURN_MAP_FROM_STRING = getMethodDescriptor(Type.getType(Map.class), STRING_TYPE);

    private static final Type PROCESS_TYPE = Type.getType(Process.class);
    private static final Type PROCESS_BUILDER_TYPE = Type.getType(ProcessBuilder.class);
    private static final Type RUNTIME_TYPE = Type.getType(Runtime.class);
    private static final Type PROCESS_GROOVY_METHODS_TYPE = Type.getType(ProcessGroovyMethods.class);
    private static final Type STRING_ARRAY_TYPE = Type.getType(String[].class);
    private static final Type FILE_TYPE = Type.getType(File.class);
    private static final Type LIST_TYPE = Type.getType(List.class);

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

    @Override
    public boolean visitMethodInsn(MethodVisitorScope mv, String className, int opcode, String owner, String name, String descriptor, boolean isInterface, Supplier<MethodNode> readMethodNode) {
        switch (opcode) {
            case Opcodes.INVOKESTATIC:
                return visitINVOKESTATIC(mv, className, owner, name, descriptor);
            case Opcodes.INVOKEVIRTUAL:
                return visitINVOKEVIRTUAL(mv, className, owner, name, descriptor);
        }
        return false;
    }

    @Override
    public BytecodeInterceptorType getType() {
        return BytecodeInterceptorType.INSTRUMENTATION;
    }

    private boolean visitINVOKESTATIC(MethodVisitorScope mv, String className, String owner, String name, String descriptor) {
        // TODO - load the class literal instead of class name to pass to the methods on Instrumented
        if (owner.equals(SYSTEM_TYPE.getInternalName())) {
            if (name.equals("getProperty")) {
                if (descriptor.equals(RETURN_STRING_FROM_STRING)) {
                    mv._LDC(binaryClassNameOf(className));
                    mv._INVOKESTATIC(INSTRUMENTED_TYPE, "systemProperty", RETURN_STRING_FROM_STRING_STRING);
                    return true;
                }
                if (descriptor.equals(RETURN_STRING_FROM_STRING_STRING)) {
                    mv._LDC(binaryClassNameOf(className));
                    mv._INVOKESTATIC(INSTRUMENTED_TYPE, "systemProperty", RETURN_STRING_FROM_STRING_STRING_STRING);
                    return true;
                }
            } else if (name.equals("getProperties") && descriptor.equals(RETURN_PROPERTIES)) {
                mv._LDC(binaryClassNameOf(className));
                mv._INVOKESTATIC(INSTRUMENTED_TYPE, "systemProperties", RETURN_PROPERTIES_FROM_STRING);
                return true;
            } else if (name.equals("setProperties") && descriptor.equals(RETURN_VOID_FROM_PROPERTIES)) {
                mv._LDC(binaryClassNameOf(className));
                mv._INVOKESTATIC(INSTRUMENTED_TYPE, "setSystemProperties", RETURN_VOID_FROM_PROPERTIES_STRING);
                return true;
            } else if (name.equals("setProperty") && descriptor.equals(RETURN_STRING_FROM_STRING_STRING)) {
                mv._LDC(binaryClassNameOf(className));
                mv._INVOKESTATIC(INSTRUMENTED_TYPE, "setSystemProperty", RETURN_STRING_FROM_STRING_STRING_STRING);
                return true;
            } else if (name.equals("clearProperty") && descriptor.equals(RETURN_STRING_FROM_STRING)) {
                mv._LDC(binaryClassNameOf(className));
                mv._INVOKESTATIC(INSTRUMENTED_TYPE, "clearSystemProperty", RETURN_STRING_FROM_STRING_STRING);
                return true;
            } else if (name.equals("getenv")) {
                if (descriptor.equals(RETURN_STRING_FROM_STRING)) {
                    // System.getenv(String) -> String
                    mv._LDC(binaryClassNameOf(className));
                    mv._INVOKESTATIC(INSTRUMENTED_TYPE, "getenv", RETURN_STRING_FROM_STRING_STRING);
                    return true;
                } else if (descriptor.equals(RETURN_MAP)) {
                    // System.getenv() -> Map<String, String>
                    mv._LDC(binaryClassNameOf(className));
                    mv._INVOKESTATIC(INSTRUMENTED_TYPE, "getenv", RETURN_MAP_FROM_STRING);
                    return true;
                }
            }
        } else if (owner.equals(INTEGER_TYPE.getInternalName()) && name.equals("getInteger")) {
            if (descriptor.equals(RETURN_INTEGER_FROM_STRING)) {
                mv._LDC(binaryClassNameOf(className));
                mv._INVOKESTATIC(INSTRUMENTED_TYPE, "getInteger", RETURN_INTEGER_FROM_STRING_STRING);
                return true;
            }
            if (descriptor.equals(RETURN_INTEGER_FROM_STRING_INT)) {
                mv._LDC(binaryClassNameOf(className));
                mv._INVOKESTATIC(INSTRUMENTED_TYPE, "getInteger", RETURN_INTEGER_FROM_STRING_INT_STRING);
                return true;
            }
            if (descriptor.equals(RETURN_INTEGER_FROM_STRING_INTEGER)) {
                mv._LDC(binaryClassNameOf(className));
                mv._INVOKESTATIC(INSTRUMENTED_TYPE, "getInteger", RETURN_INTEGER_FROM_STRING_INTEGER_STRING);
                return true;
            }
        } else if (owner.equals(LONG_TYPE.getInternalName()) && name.equals("getLong")) {
            if (descriptor.equals(RETURN_LONG_FROM_STRING)) {
                mv._LDC(binaryClassNameOf(className));
                mv._INVOKESTATIC(INSTRUMENTED_TYPE, "getLong", RETURN_LONG_FROM_STRING_STRING);
                return true;
            }
            if (descriptor.equals(RETURN_LONG_FROM_STRING_PRIMITIVE_LONG)) {
                mv._LDC(binaryClassNameOf(className));
                mv._INVOKESTATIC(INSTRUMENTED_TYPE, "getLong", RETURN_LONG_FROM_STRING_PRIMITIVE_LONG_STRING);
                return true;
            }
            if (descriptor.equals(RETURN_LONG_FROM_STRING_LONG)) {
                mv._LDC(binaryClassNameOf(className));
                mv._INVOKESTATIC(INSTRUMENTED_TYPE, "getLong", RETURN_LONG_FROM_STRING_LONG_STRING);
                return true;
            }
        } else if (owner.equals(BOOLEAN_TYPE.getInternalName()) && name.equals("getBoolean") && descriptor.equals(RETURN_PRIMITIVE_BOOLEAN_FROM_STRING)) {
            mv._LDC(binaryClassNameOf(className));
            mv._INVOKESTATIC(INSTRUMENTED_TYPE, "getBoolean", RETURN_PRIMITIVE_BOOLEAN_FROM_STRING_STRING);
            return true;
        } else if (owner.equals(PROCESS_GROOVY_METHODS_TYPE.getInternalName()) && name.equals("execute")) {
            Optional<String> instrumentedDescriptor = getInstrumentedDescriptorForProcessGroovyMethodsExecuteDescriptor(descriptor);
            if (!instrumentedDescriptor.isPresent()) {
                return false;
            }
            mv._LDC(binaryClassNameOf(className));
            mv._INVOKESTATIC(INSTRUMENTED_TYPE, "execute", instrumentedDescriptor.get());
            return true;
        }
        if (owner.equals(PROCESS_BUILDER_TYPE.getInternalName()) && name.equals("startPipeline") && descriptor.equals(RETURN_LIST_FROM_LIST)) {
            mv._LDC(binaryClassNameOf(className));
            mv._INVOKESTATIC(INSTRUMENTED_TYPE, "startPipeline", RETURN_LIST_FROM_LIST_STRING);
            return true;
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

    private boolean visitINVOKEVIRTUAL(MethodVisitorScope mv, String className, String owner, String name, String descriptor) {
        // Runtime.exec(...)
        if (owner.equals(RUNTIME_TYPE.getInternalName()) && name.equals("exec")) {
            Optional<String> instrumentedDescriptor = getInstrumentedDescriptorForRuntimeExecDescriptor(descriptor);
            if (!instrumentedDescriptor.isPresent()) {
                return false;
            }
            mv._LDC(binaryClassNameOf(className));
            mv._INVOKESTATIC(INSTRUMENTED_TYPE, "exec", instrumentedDescriptor.get());
            return true;
        }
        if (owner.equals(PROCESS_BUILDER_TYPE.getInternalName())) {
            if (name.equals("start") && descriptor.equals(RETURN_PROCESS)) {
                mv._LDC(binaryClassNameOf(className));
                mv._INVOKESTATIC(INSTRUMENTED_TYPE, "start", RETURN_PROCESS_FROM_PROCESS_BUILDER_STRING);
                return true;
            }
        }
        return false;
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

    private String binaryClassNameOf(String className) {
        return getObjectType(className).getClassName();
    }

    @Nullable
    @Override
    public BridgeMethodBuilder findBridgeMethodBuilder(String className, int tag, String owner, String name, String descriptor) {
        if (tag == Opcodes.H_INVOKESTATIC && owner.equals(SYSTEM_TYPE.getInternalName()) && name.equals("getProperty") && descriptor.equals(RETURN_STRING_FROM_STRING)) {
            return DefaultBridgeMethodBuilder.create(tag, owner, descriptor, INSTRUMENTED_TYPE.getInternalName(), "systemProperty", RETURN_STRING_FROM_STRING_STRING).withClassName(className);
        }
        // TODO(mlopatkin): intercept the rest of the methods or migrate them to annotation processor.
        return null;
    }
}
