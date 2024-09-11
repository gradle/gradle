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

package org.gradle.internal.classpath.declarations;

import groovy.lang.Closure;
import groovy.lang.GroovyObject;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.codehaus.groovy.runtime.ScriptBytecodeAdapter;
import org.gradle.api.NonNullApi;
import org.gradle.internal.classpath.InstrumentedClosuresHelper;
import org.gradle.internal.classpath.intercept.CallInterceptor;
import org.gradle.internal.classpath.intercept.CallInterceptorResolver;
import org.gradle.internal.classpath.intercept.CallInterceptorResolver.ClosureCallInterceptorResolver;
import org.gradle.internal.classpath.intercept.InterceptScope;
import org.gradle.internal.classpath.intercept.InvocationImpl;
import org.gradle.internal.instrumentation.api.annotations.CallableKind;
import org.gradle.internal.instrumentation.api.annotations.InterceptJvmCalls;
import org.gradle.internal.instrumentation.api.annotations.ParameterKind.CallerClassName;
import org.gradle.internal.instrumentation.api.annotations.ParameterKind.InjectVisitorContext;
import org.gradle.internal.instrumentation.api.annotations.SpecificJvmCallInterceptors;
import org.gradle.internal.instrumentation.api.declarations.InterceptorDeclaration;
import org.gradle.internal.instrumentation.api.types.BytecodeInterceptorFilter;

import javax.annotation.Nullable;

import static org.gradle.internal.classpath.InstrumentedGroovyCallsHelper.withEntryPoint;
import static org.gradle.internal.classpath.InstrumentedGroovyCallsTracker.CallKind.SET_PROPERTY;

@SuppressWarnings("NewMethodNamingConvention")
@NonNullApi
@SpecificJvmCallInterceptors(generatedClassName = InterceptorDeclaration.JVM_BYTECODE_GENERATED_CLASS_NAME_FOR_CONFIG_CACHE)
public class GroovyDynamicDispatchInterceptors {
    @InterceptJvmCalls
    @CallableKind.StaticMethod(ofClass = ScriptBytecodeAdapter.class)
    public static void intercept_setGroovyObjectProperty(
        Object messageArgument,
        /* Sometimes it's null, we can't rely on it, so we still use @CallerClassName */
        Class<?> senderClass,
        GroovyObject receiver,
        String messageName,
        @CallerClassName String consumer,
        @InjectVisitorContext BytecodeInterceptorFilter interceptorFilter
    ) throws Throwable {
        if (!ClosureCallInterceptorResolver.of(interceptorFilter).isAwareOfCallSiteName(messageName)) {
            ScriptBytecodeAdapter.setGroovyObjectProperty(messageArgument, senderClass, receiver, messageName);
            return;
        }
        InstrumentedClosuresHelper.INSTANCE.hitInstrumentedDynamicCall();
        withEntryPoint(consumer, messageName, SET_PROPERTY, () -> {
            ScriptBytecodeAdapter.setGroovyObjectProperty(messageArgument, senderClass, receiver, messageName);
            return null;
        });
    }

    @InterceptJvmCalls
    @CallableKind.StaticMethod(ofClass = ScriptBytecodeAdapter.class)
    public static void intercept_setProperty(
        Object messageArgument,
        /* Sometimes it's null, we can't rely on it, so we still use @CallerClassName */
        Class<?> senderClass,
        Object receiver,
        String messageName,
        @CallerClassName String consumer,
        @InjectVisitorContext BytecodeInterceptorFilter interceptorFilter
    ) throws Throwable {
        CallInterceptorResolver interceptorResolver = ClosureCallInterceptorResolver.of(interceptorFilter);
        CallInterceptor interceptor = interceptorResolver.resolveCallInterceptor(InterceptScope.writesOfPropertiesNamed(messageName));
        if (interceptor != null) {
            Object[] args = new Object[]{messageArgument};
            interceptor.intercept(new InvocationImpl<>(receiver, args, () -> {
                callInstrumentedSetProperty(messageArgument, senderClass, receiver, messageName, consumer, interceptorFilter);
                return null;
            }), consumer);
        } else {
            callInstrumentedSetProperty(messageArgument, senderClass, receiver, messageName, consumer, interceptorFilter);
        }
    }

    private static void callInstrumentedSetProperty(
        Object messageArgument,
        Class<?> senderClass,
        Object receiver,
        String messageName,
        String consumer,
        BytecodeInterceptorFilter interceptorFilter
    ) throws Throwable {
        if (!ClosureCallInterceptorResolver.of(interceptorFilter).isAwareOfCallSiteName(messageName)) {
            ScriptBytecodeAdapter.setProperty(messageArgument, senderClass, receiver, messageName);
            return;
        }
        InstrumentedClosuresHelper.INSTANCE.hitInstrumentedDynamicCall();
        withEntryPoint(consumer, messageName, SET_PROPERTY, () -> {
            ScriptBytecodeAdapter.setProperty(messageArgument, senderClass, receiver, messageName);
            return null;
        });
    }

    @InterceptJvmCalls
    @CallableKind.StaticMethod(ofClass = ScriptBytecodeAdapter.class)
    public static Closure<?> intercept_getMethodPointer(
        Object owner,
        @Nullable String methodName,
        @CallerClassName String consumer,
        @InjectVisitorContext BytecodeInterceptorFilter interceptorFilter
    ) {
        Closure<?> originalPointer = ScriptBytecodeAdapter.getMethodPointer(owner, methodName);

        if (methodName == null) {
            // It isn't clear if "null" is an allowed method name, but we cannot intercept it anyway.
            return originalPointer;
        }
        // TODO(mlopatkin): ClosureCallInterceptorResolver is a strange name for a general interceptor-providing routine.
        CallInterceptorResolver resolver = ClosureCallInterceptorResolver.of(interceptorFilter);
        InterceptScope scope = isConstructorMethodRef(owner, methodName)
            ? InterceptScope.constructorsOf((Class<?>) owner)
            : InterceptScope.methodsNamed(methodName);
        CallInterceptor interceptor = resolver.resolveCallInterceptor(scope);
        if (interceptor == null) {
            return originalPointer;
        }

        class MethodRefInterceptorClosure extends Closure<Object> {
            public MethodRefInterceptorClosure() {
                super(owner);
            }

            @SuppressWarnings("unused")  // Called by Groovy runtime
            @Nullable
            public Object doCall(Object... arguments) throws Throwable {
                Object owner = getOwner();
                // Method pointers and method references may be bound (foo::getBar) or unbound (Foo::getBar).
                // The bound pointer has the receiver as the owner.
                // The unbound one has a class as an owner and gets the receiver as a first argument when invoked.
                // The latter is therefore indistinguishable from a static method that receives an instance of owner as a first argument.
                // However, the instance method interceptor won't match the arguments of the static-like invocation, and some adaptation is needed.
                // Let's try to intercept without any preparation first, to avoid extra work on a happy path.
                // This covers static methods and bound instance invocations.
                return interceptor.intercept(
                    new InvocationImpl<>(owner, arguments, () -> {
                        // If we're here, a static method or bound instance method invocation weren't intercepted.
                        // We can still try to intercept an unbound invocation.
                        if (canBeUnboundInstanceMethodInvocation(owner, arguments)) {
                            Object maybeInstanceReceiver = arguments[0];
                            Object[] boundCallArguments = subArray(arguments, 1);
                            // Let's try to intercept instance method and fall back to the original call if it also fails.
                            return interceptor.intercept(
                                new InvocationImpl<>(maybeInstanceReceiver, boundCallArguments, () -> InvokerHelper.invokeClosure(originalPointer, arguments)),
                                consumer
                            );
                        }
                        return InvokerHelper.invokeClosure(originalPointer, arguments);
                    }),
                    consumer
                );
            }

            private boolean canBeUnboundInstanceMethodInvocation(Object owner, Object[] arguments) {
                return arguments.length > 0 && owner instanceof Class<?> && ((Class<?>) owner).isInstance(arguments[0]);
            }

            private Object[] subArray(Object[] array, int pos) {
                Object[] subArray = new Object[array.length - pos];
                System.arraycopy(array, pos, subArray, 0, subArray.length);
                return subArray;
            }
        }

        return new MethodRefInterceptorClosure();
    }

    private static boolean isConstructorMethodRef(Object owner, String methodName) {
        return "new".equals(methodName) && owner instanceof Class<?>;
    }
}
