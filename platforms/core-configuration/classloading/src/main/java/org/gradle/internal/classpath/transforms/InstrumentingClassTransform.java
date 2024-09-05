/*
 * Copyright 2023 the original author or authors.
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

import com.google.common.collect.ImmutableList;
import org.apache.commons.lang3.ArrayUtils;
import org.codehaus.groovy.runtime.callsite.CallSiteArray;
import org.codehaus.groovy.vmplugin.v8.IndyInterface;
import org.gradle.api.file.RelativePath;
import org.gradle.internal.Pair;
import org.gradle.internal.classpath.CallInterceptionClosureInstrumentingClassVisitor;
import org.gradle.internal.classpath.ClassData;
import org.gradle.internal.classpath.ClasspathEntryVisitor;
import org.gradle.internal.classpath.Instrumented;
import org.gradle.internal.classpath.intercept.CallInterceptorRegistry;
import org.gradle.internal.classpath.intercept.JvmBytecodeInterceptorSet;
import org.gradle.internal.classpath.types.InstrumentationTypeRegistry;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.instrumentation.api.jvmbytecode.BridgeMethodBuilder;
import org.gradle.internal.instrumentation.api.jvmbytecode.JvmBytecodeCallInterceptor;
import org.gradle.internal.instrumentation.api.metadata.InstrumentationMetadata;
import org.gradle.internal.instrumentation.api.types.BytecodeInterceptorFilter;
import org.gradle.internal.instrumentation.reporting.listener.MethodInterceptionListener;
import org.gradle.internal.lazy.Lazy;
import org.gradle.model.internal.asm.MethodVisitorScope;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;

import javax.annotation.Nullable;
import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static org.gradle.internal.classpath.transforms.CommonTypes.NO_EXCEPTIONS;
import static org.gradle.internal.classpath.transforms.CommonTypes.STRING_TYPE;
import static org.gradle.internal.instrumentation.api.types.BytecodeInterceptorFilter.INSTRUMENTATION_ONLY;
import static org.gradle.model.internal.asm.AsmConstants.ASM_LEVEL;
import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ACC_SYNTHETIC;
import static org.objectweb.asm.Opcodes.H_INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Type.INT_TYPE;
import static org.objectweb.asm.Type.getMethodDescriptor;
import static org.objectweb.asm.Type.getType;

public class InstrumentingClassTransform implements ClassTransform {

    /**
     * Decoration format. Increment this when making changes.
     */
    private static final int DECORATION_FORMAT = 36;

    private static final Type INSTRUMENTED_TYPE = getType(Instrumented.class);
    private static final Type BYTECODE_INTERCEPTOR_FILTER_TYPE = Type.getType(BytecodeInterceptorFilter.class);

    private static final String RETURN_CALL_SITE_ARRAY = getMethodDescriptor(getType(CallSiteArray.class));
    private static final String RETURN_VOID_FROM_CALL_SITE_ARRAY_BYTECODE_INTERCEPTOR = getMethodDescriptor(Type.VOID_TYPE, getType(CallSiteArray.class), BYTECODE_INTERCEPTOR_FILTER_TYPE);

    private static final String GROOVY_INDY_INTERFACE_TYPE = getType(IndyInterface.class).getInternalName();

    @SuppressWarnings("deprecation")
    private static final String GROOVY_INDY_INTERFACE_V7_TYPE = getType(org.codehaus.groovy.vmplugin.v7.IndyInterface.class).getInternalName();
    private static final String GROOVY_INDY_INTERFACE_BOOTSTRAP_METHOD_DESCRIPTOR = getMethodDescriptor(getType(CallSite.class), getType(MethodHandles.Lookup.class), STRING_TYPE, getType(MethodType.class), STRING_TYPE, INT_TYPE);
    private static final String INSTRUMENTED_GROOVY_INDY_INTERFACE_BOOTSTRAP_METHOD_DESCRIPTOR = getMethodDescriptor(getType(CallSite.class), getType(MethodHandles.Lookup.class), STRING_TYPE, getType(MethodType.class), STRING_TYPE, INT_TYPE, STRING_TYPE);

    private static final String INSTRUMENTED_CALL_SITE_METHOD = "$instrumentedCallSiteArray";
    private static final String CREATE_CALL_SITE_ARRAY_METHOD = "$createCallSiteArray";

    private static final AdhocInterceptors ADHOC_INTERCEPTORS = new AdhocInterceptors();

    private final JvmBytecodeInterceptorSet externalInterceptors;
    private final MethodInterceptionListener methodInterceptionListener;
    private final InstrumentationMetadata instrumentationMetadata;

    @Override
    public void applyConfigurationTo(Hasher hasher) {
        hasher.putString(InstrumentingClassTransform.class.getSimpleName());
        hasher.putInt(DECORATION_FORMAT);
    }

    public InstrumentingClassTransform() {
        this(INSTRUMENTATION_ONLY, InstrumentationTypeRegistry.EMPTY);
    }

    public InstrumentingClassTransform(BytecodeInterceptorFilter interceptorFilter, InstrumentationTypeRegistry typeRegistry) {
        this(interceptorFilter, typeRegistry, MethodInterceptionListener.NO_OP);
    }

    public InstrumentingClassTransform(BytecodeInterceptorFilter interceptorFilter, InstrumentationTypeRegistry typeRegistry, MethodInterceptionListener methodInterceptionListener) {
        this.externalInterceptors = CallInterceptorRegistry.getJvmBytecodeInterceptors(interceptorFilter);
        this.methodInterceptionListener = methodInterceptionListener;
        this.instrumentationMetadata = (type, superType) -> typeRegistry.getSuperTypes(type).contains(superType);
    }

    private BytecodeInterceptorFilter interceptorFilter() {
        return externalInterceptors.getOriginalFilter();
    }

    private List<JvmBytecodeCallInterceptor> buildInterceptors(InstrumentationMetadata metadata) {
        return externalInterceptors.getInterceptors(metadata);
    }

    @Override
    public Pair<RelativePath, ClassVisitor> apply(ClasspathEntryVisitor.Entry entry, ClassVisitor visitor, ClassData classData) {
        // TODO(mlopatkin) can we reuse interceptors in a bigger scope, not per class, but per artifact?
        List<JvmBytecodeCallInterceptor> interceptors = buildInterceptors(instrumentationMetadata);
        if (interceptorFilter().matches(ADHOC_INTERCEPTORS)) {
            interceptors = ImmutableList.<JvmBytecodeCallInterceptor>builderWithExpectedSize(interceptors.size() + 1).add(ADHOC_INTERCEPTORS).addAll(interceptors).build();
        }
        return Pair.of(entry.getPath(),
            new InstrumentingVisitor(
                new CallInterceptionClosureInstrumentingClassVisitor(
                    new LambdaSerializationTransformer(new InstrumentingBackwardsCompatibilityVisitor(visitor)),
                    interceptorFilter()
                ),
                classData,
                interceptors,
                interceptorFilter(),
                methodInterceptionListener
            )
        );
    }

    private static class BridgeMethod {
        final Handle bridgeMethodHandle;
        final BridgeMethodBuilder bridgeMethodBuilder;

        private BridgeMethod(Handle bridgeMethodHandle, BridgeMethodBuilder bridgeMethodBuilder) {
            this.bridgeMethodHandle = bridgeMethodHandle;
            this.bridgeMethodBuilder = bridgeMethodBuilder;
        }
    }

    private static class InstrumentingVisitor extends ClassVisitor {
        private final ClassData classData;
        private final List<JvmBytecodeCallInterceptor> interceptors;
        private final BytecodeInterceptorFilter interceptorFilter;

        private final Map<Handle, BridgeMethod> bridgeMethods = new LinkedHashMap<>();
        private final MethodInterceptionListener methodInterceptionListener;
        private int nextBridgeMethodIndex;

        private String className;
        private String sourceFileName;
        private boolean hasGroovyCallSites;

        public InstrumentingVisitor(
            ClassVisitor visitor,
            ClassData classData,
            List<JvmBytecodeCallInterceptor> interceptors,
            BytecodeInterceptorFilter interceptorFilter,
            MethodInterceptionListener methodInterceptionListener
        ) {
            super(ASM_LEVEL, visitor);
            this.classData = classData;
            this.interceptors = interceptors;
            this.interceptorFilter = interceptorFilter;
            this.methodInterceptionListener = methodInterceptionListener;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            super.visit(version, access, name, signature, superName, interfaces);
            this.className = name;
        }

        @Override
        public void visitSource(String source, String debug) {
            this.sourceFileName = source;
            super.visitSource(source, debug);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            if (name.equals(CREATE_CALL_SITE_ARRAY_METHOD) && descriptor.equals(RETURN_CALL_SITE_ARRAY)) {
                hasGroovyCallSites = true;
            }
            MethodVisitor methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);
            Lazy<MethodNode> asMethodNode = Lazy.unsafe().of(() -> {
                Optional<MethodNode> methodNode = classData.readClassAsNode().methods.stream().filter(method ->
                    Objects.equals(method.name, name) && Objects.equals(method.desc, descriptor) && Objects.equals(method.signature, signature)
                ).findFirst();
                return methodNode.orElseThrow(() -> new IllegalStateException("could not find method " + name + " with descriptor " + descriptor));
            });
            return new InstrumentingMethodVisitor(this, methodVisitor, asMethodNode);
        }

        @Override
        public void visitEnd() {
            if (hasGroovyCallSites) {
                generateCallSiteFactoryMethod();
            }
            bridgeMethods.values().forEach(this::generateBridgeMethod);
            super.visitEnd();
        }

        private void generateCallSiteFactoryMethod() {
            new MethodVisitorScope(visitStaticPrivateMethod(INSTRUMENTED_CALL_SITE_METHOD, RETURN_CALL_SITE_ARRAY)) {{
                _INVOKESTATIC(className, CREATE_CALL_SITE_ARRAY_METHOD, RETURN_CALL_SITE_ARRAY);
                _DUP();
                _GETSTATIC(BYTECODE_INTERCEPTOR_FILTER_TYPE, interceptorFilter.name(), BYTECODE_INTERCEPTOR_FILTER_TYPE.getDescriptor());
                _INVOKESTATIC(INSTRUMENTED_TYPE, "groovyCallSites", RETURN_VOID_FROM_CALL_SITE_ARRAY_BYTECODE_INTERCEPTOR);
                _ARETURN();
                visitMaxs(2, 0);
                visitEnd();
            }};
        }

        private void generateBridgeMethod(BridgeMethod bridgeMethod) {
            bridgeMethod.bridgeMethodBuilder.buildBridgeMethod(visitStaticPrivateMethod(bridgeMethod.bridgeMethodHandle.getName(), bridgeMethod.bridgeMethodHandle.getDesc()));
        }

        private MethodVisitor visitStaticPrivateMethod(String name, String descriptor) {
            return super.visitMethod(ACC_STATIC | ACC_SYNTHETIC | ACC_PRIVATE, name, descriptor, null, NO_EXCEPTIONS);
        }

        /**
         * Finds the {@link BridgeMethod} for the method handle. May return null if nothing intercepts the method.
         * For each method at most one bridge method is produced, regardless the number of handles encountered
         * (i.e. all method references to e.g. {@code ProcessBuilder::start} in the class are re-routed to a single bridge method).
         *
         * @param originalHandle the original method handle
         * @return the bridge method that intercepts the original method or null if there is no interceptor
         */
        @Nullable
        public BridgeMethod findBridgeMethodFor(Handle originalHandle) {
            return bridgeMethods.computeIfAbsent(originalHandle, this::maybeBuildBridgeMethod);
        }

        @Nullable
        private BridgeMethod maybeBuildBridgeMethod(Handle handle) {
            for (JvmBytecodeCallInterceptor interceptor : interceptors) {
                BridgeMethodBuilder methodBuilder = interceptor.findBridgeMethodBuilder(className, handle.getTag(), handle.getOwner(), handle.getName(), handle.getDesc());
                if (methodBuilder != null) {
                    return new BridgeMethod(makeBridgeMethodHandle(makeBridgeMethodName(handle), methodBuilder.getBridgeMethodDescriptor()), methodBuilder);
                }
            }
            return null;
        }

        /**
         * Builds a unique bridge method name for the given method handle based on the owner and the name of the original method.
         * For example, a bridge method for {@code com.foo.Bar.baz(...)} will be named {@code gradle$intercept$$com$foo$Bar$$baz$<N>},
         * where {@code <N>} is a number to make the resulting name unique. The number starts from 0 and increases each time this method is called.
         * <p>
         * Note that calling this method multiple times returns different names for the same bridge method.
         * <p>
         * Most of the name decorations are added only to make stack traces easier to understand.
         *
         * @param originalHandle the original method handle to build bridge method for
         * @return the unique bridge method name.
         */
        private String makeBridgeMethodName(Handle originalHandle) {
            // Index ensures that the generated name is unique for this class.
            int index = nextBridgeMethodIndex++;
            // com/foo/Bar -> com$foo$Bar
            String mangledOwner = originalHandle.getOwner().replace("/", "$");
            // Only <init> and <clinit> are allowed to have <> in the name.
            // As we're intercepting these too, we strip prohibited symbols from the bridge method's name.
            String safeName = originalHandle.getName().replace("<", "_").replace(">", "_");
            return "gradle$intercept$$" + mangledOwner + "$$" + safeName + "$" + index;
        }

        private Handle makeBridgeMethodHandle(String name, String desc) {
            return new Handle(H_INVOKESTATIC, className, name, desc, false);
        }
    }

    private static class InstrumentingMethodVisitor extends MethodVisitorScope {
        private final InstrumentingVisitor owner;
        private final String className;
        private final Lazy<MethodNode> asNode;
        private final Collection<JvmBytecodeCallInterceptor> interceptors;
        private final BytecodeInterceptorFilter interceptorFilter;
        private final MethodInterceptionListener methodInterceptionListener;
        private final String sourceFileName;
        private int methodInsLineNumber;

        public InstrumentingMethodVisitor(
            InstrumentingVisitor owner,
            MethodVisitor methodVisitor,
            Lazy<MethodNode> asNode
        ) {
            super(methodVisitor);
            this.owner = owner;
            this.className = owner.className;
            this.sourceFileName = owner.sourceFileName;
            this.asNode = asNode;
            this.interceptors = owner.interceptors;
            this.interceptorFilter = owner.interceptorFilter;
            this.methodInterceptionListener = owner.methodInterceptionListener;
        }

        @Override
        public void visitLineNumber(int line, Label start) {
            methodInsLineNumber = line;
            super.visitLineNumber(line, start);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            if (opcode == INVOKESTATIC && visitINVOKESTATIC(owner, name, descriptor)) {
                return;
            }

            for (JvmBytecodeCallInterceptor interceptor : interceptors) {
                if (interceptor.visitMethodInsn(this, className, opcode, owner, name, descriptor, isInterface, asNode)) {
                    methodInterceptionListener.onInterceptedMethodInstruction(
                        interceptor.getType(),
                        sourceFileName,
                        className,
                        owner,
                        name,
                        descriptor,
                        methodInsLineNumber
                    );
                    return;
                }
            }
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        }

        private boolean visitINVOKESTATIC(String owner, String name, String descriptor) {
            if (owner.equals(className) && name.equals(CREATE_CALL_SITE_ARRAY_METHOD) && descriptor.equals(RETURN_CALL_SITE_ARRAY)) {
                _INVOKESTATIC(className, INSTRUMENTED_CALL_SITE_METHOD, RETURN_CALL_SITE_ARRAY);
                return true;
            }
            return false;
        }

        @Override
        public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
            if (isGroovyIndyCallsite(bootstrapMethodHandle)) {
                // Handle for org.gradle.internal.classpath.Instrumented.bootstrap() method
                Handle interceptor = new Handle(
                    H_INVOKESTATIC,
                    INSTRUMENTED_TYPE.getInternalName(),
                    "bootstrap",
                    INSTRUMENTED_GROOVY_INDY_INTERFACE_BOOTSTRAP_METHOD_DESCRIPTOR,
                    false
                );
                bootstrapMethodArguments = ArrayUtils.add(bootstrapMethodArguments, interceptorFilter.name());
                super.visitInvokeDynamicInsn(name, descriptor, interceptor, bootstrapMethodArguments);
            } else {
                for (int i = 0; i < bootstrapMethodArguments.length; i++) {
                    bootstrapMethodArguments[i] = maybeInstrumentBootstrapArgument(bootstrapMethodArguments[i]);
                }
                super.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);
            }
        }

        private boolean isGroovyIndyCallsite(Handle bootstrapMethodHandle) {
            return (bootstrapMethodHandle.getOwner().equals(GROOVY_INDY_INTERFACE_TYPE) ||
                bootstrapMethodHandle.getOwner().equals(GROOVY_INDY_INTERFACE_V7_TYPE)) &&
                bootstrapMethodHandle.getName().equals("bootstrap") &&
                bootstrapMethodHandle.getDesc().equals(GROOVY_INDY_INTERFACE_BOOTSTRAP_METHOD_DESCRIPTOR);
        }

        private Object maybeInstrumentBootstrapArgument(Object argument) {
            if (argument instanceof Handle) {
                return maybeInstrumentHandle((Handle) argument);
            }
            return argument;
        }

        private Handle maybeInstrumentHandle(Handle handle) {
            BridgeMethod bridgeMethod = owner.findBridgeMethodFor(handle);
            if (bridgeMethod != null) {
                return bridgeMethod.bridgeMethodHandle;
            }
            return handle; // No instrumentation requested.
        }
    }
}
