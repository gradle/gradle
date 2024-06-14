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
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.instrumentation.api.jvmbytecode.JvmBytecodeCallInterceptor;
import org.gradle.internal.instrumentation.api.types.BytecodeInterceptorFilter;
import org.gradle.internal.lazy.Lazy;
import org.gradle.model.internal.asm.MethodVisitorScope;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.gradle.internal.classanalysis.AsmConstants.ASM_LEVEL;
import static org.gradle.internal.classpath.transforms.CommonTypes.NO_EXCEPTIONS;
import static org.gradle.internal.classpath.transforms.CommonTypes.STRING_TYPE;
import static org.gradle.internal.instrumentation.api.types.BytecodeInterceptorFilter.INSTRUMENTATION_ONLY;
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

    private static final String INSTRUMENTED_CALL_SITE_METHOD = "$instrumentedCallSiteArray";
    private static final String CREATE_CALL_SITE_ARRAY_METHOD = "$createCallSiteArray";

    private final JvmBytecodeInterceptorSet externalInterceptors;

    @Override
    public void applyConfigurationTo(Hasher hasher) {
        hasher.putString(InstrumentingClassTransform.class.getSimpleName());
        hasher.putInt(DECORATION_FORMAT);
    }

    public InstrumentingClassTransform() {
        this(INSTRUMENTATION_ONLY);
    }

    public InstrumentingClassTransform(BytecodeInterceptorFilter interceptorFilter) {
        this.externalInterceptors = CallInterceptorRegistry.getJvmBytecodeInterceptors(interceptorFilter);
    }

    @Override
    public Pair<RelativePath, ClassVisitor> apply(ClasspathEntryVisitor.Entry entry, ClassVisitor visitor, ClassData classData) {
        return Pair.of(entry.getPath(),
            new InstrumentingVisitor(
                new CallInterceptionClosureInstrumentingClassVisitor(
                    new LambdaSerializationTransformer(new InstrumentingBackwardsCompatibilityVisitor(visitor)),
                    externalInterceptors.getOriginalFilter()
                ),
                classData, externalInterceptors
            )
        );
    }

    private static class InstrumentingVisitor extends ClassVisitor {
        String className;
        private final ClassData classData;
        private boolean hasGroovyCallSites;
        private final JvmBytecodeInterceptorSet externalInterceptors;

        public InstrumentingVisitor(ClassVisitor visitor, ClassData classData, JvmBytecodeInterceptorSet externalInterceptors) {
            super(ASM_LEVEL, visitor);
            this.classData = classData;
            this.externalInterceptors = externalInterceptors;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            super.visit(version, access, name, signature, superName, interfaces);
            this.className = name;
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
            return new InstrumentingMethodVisitor(this, methodVisitor, asMethodNode, classData, externalInterceptors);
        }

        @Override
        public void visitEnd() {
            if (hasGroovyCallSites) {
                generateCallSiteFactoryMethod();
            }
            super.visitEnd();
        }

        private void generateCallSiteFactoryMethod() {
            new MethodVisitorScope(visitStaticPrivateMethod(INSTRUMENTED_CALL_SITE_METHOD, RETURN_CALL_SITE_ARRAY)) {{
                _INVOKESTATIC(className, CREATE_CALL_SITE_ARRAY_METHOD, RETURN_CALL_SITE_ARRAY);
                _DUP();
                _GETSTATIC(BYTECODE_INTERCEPTOR_FILTER_TYPE, externalInterceptors.getOriginalFilter().name(), BYTECODE_INTERCEPTOR_FILTER_TYPE.getDescriptor());
                _INVOKESTATIC(INSTRUMENTED_TYPE, "groovyCallSites", RETURN_VOID_FROM_CALL_SITE_ARRAY_BYTECODE_INTERCEPTOR);
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
        private static final AdhocInterceptors ADHOC_INTERCEPTORS = new AdhocInterceptors();

        private final InstrumentingVisitor owner;
        private final String className;
        private final Lazy<MethodNode> asNode;
        private final Collection<JvmBytecodeCallInterceptor> interceptors;
        private final BytecodeInterceptorFilter interceptorFilter;

        public InstrumentingMethodVisitor(InstrumentingVisitor owner, MethodVisitor methodVisitor, Lazy<MethodNode> asNode, ClassData classData, JvmBytecodeInterceptorSet interceptors) {
            super(methodVisitor);
            this.owner = owner;
            this.className = owner.className;
            this.asNode = asNode;
            this.interceptorFilter = interceptors.getOriginalFilter();
            List<JvmBytecodeCallInterceptor> providedInterceptors = interceptors.getInterceptors(classData);
            this.interceptors = interceptorFilter.matches(ADHOC_INTERCEPTORS)
                ? ImmutableList.<JvmBytecodeCallInterceptor>builderWithExpectedSize(providedInterceptors.size() + 1).add(ADHOC_INTERCEPTORS).addAll(providedInterceptors).build()
                : providedInterceptors;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            if (opcode == INVOKESTATIC && visitINVOKESTATIC(owner, name, descriptor)) {
                return;
            }

            for (JvmBytecodeCallInterceptor interceptor : interceptors) {
                if (interceptor.visitMethodInsn(this, className, opcode, owner, name, descriptor, isInterface, asNode)) {
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
                Handle interceptor = new Handle(
                    H_INVOKESTATIC,
                    INSTRUMENTED_TYPE.getInternalName(),
                    getBoostrapMethodName(interceptorFilter),
                    GROOVY_INDY_INTERFACE_BOOTSTRAP_METHOD_DESCRIPTOR,
                    false
                );
                super.visitInvokeDynamicInsn(name, descriptor, interceptor, bootstrapMethodArguments);
            } else {
                super.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);
            }
        }

        private static String getBoostrapMethodName(BytecodeInterceptorFilter interceptorFilter) {
            switch (interceptorFilter) {
                case INSTRUMENTATION_ONLY:
                    return "bootstrapInstrumentationOnly";
                case ALL:
                    return "bootstrapAll";
                default:
                    throw new UnsupportedOperationException("Unknown interceptor request: " + interceptorFilter);
            }
        }

        private boolean isGroovyIndyCallsite(Handle bootstrapMethodHandle) {
            return (bootstrapMethodHandle.getOwner().equals(GROOVY_INDY_INTERFACE_TYPE) ||
                bootstrapMethodHandle.getOwner().equals(GROOVY_INDY_INTERFACE_V7_TYPE)) &&
                bootstrapMethodHandle.getName().equals("bootstrap") &&
                bootstrapMethodHandle.getDesc().equals(GROOVY_INDY_INTERFACE_BOOTSTRAP_METHOD_DESCRIPTOR);
        }
    }
}
