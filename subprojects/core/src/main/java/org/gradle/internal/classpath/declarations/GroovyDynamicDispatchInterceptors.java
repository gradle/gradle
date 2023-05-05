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
import org.codehaus.groovy.runtime.ScriptBytecodeAdapter;
import org.codehaus.groovy.runtime.callsite.CallSite;
import org.gradle.api.NonNullApi;
import org.gradle.internal.classpath.Instrumented;
import org.gradle.internal.classpath.InstrumentedGroovyCallsTracker;
import org.gradle.internal.classpath.InstrumentedGroovyCallsTracker.CallKind;
import org.gradle.internal.classpath.intercept.AbstractInvocation;
import org.gradle.internal.classpath.intercept.CallInterceptor;
import org.gradle.internal.classpath.intercept.InterceptScope;
import org.gradle.internal.classpath.intercept.Invocation;
import org.gradle.internal.instrumentation.api.annotations.CallableKind;
import org.gradle.internal.instrumentation.api.annotations.CallableKind.InstanceMethod;
import org.gradle.internal.instrumentation.api.annotations.InterceptJvmCalls;
import org.gradle.internal.instrumentation.api.annotations.ParameterKind.CallerClassName;
import org.gradle.internal.instrumentation.api.annotations.ParameterKind.Receiver;
import org.gradle.internal.instrumentation.api.annotations.SpecificJvmCallInterceptors;
import org.gradle.internal.instrumentation.api.declarations.InterceptorDeclaration;

import javax.annotation.Nullable;

import static org.gradle.internal.classpath.InstrumentedGroovyCallsTracker.CallKind.GET_PROPERTY;
import static org.gradle.internal.classpath.InstrumentedGroovyCallsTracker.CallKind.INVOKE_METHOD;
import static org.gradle.internal.classpath.InstrumentedGroovyCallsTracker.CallKind.SET_PROPERTY;

@SuppressWarnings("NewMethodNamingConvention")
@NonNullApi
@SpecificJvmCallInterceptors(generatedClassName = InterceptorDeclaration.JVM_BYTECODE_GENERATED_CLASS_NAME_FOR_CONFIG_CACHE)
public class GroovyDynamicDispatchInterceptors {
    @NonNullApi
    private interface ThrowingCallable<T> {
        @Nullable
        T call() throws Throwable;
    }

    private static <T> @Nullable T withEntryPoint(String consumer, String callableName, CallKind kind, ThrowingCallable<T> callable) throws Throwable {
        InstrumentedGroovyCallsTracker.EntryPointCallSite entryPoint = InstrumentedGroovyCallsTracker.enterCall(consumer, callableName, kind);
        try {
            return callable.call();
        } finally {
            InstrumentedGroovyCallsTracker.leaveCall(entryPoint);
        }
    }

    @InterceptJvmCalls
    @InstanceMethod
    public static Object intercept_callGroovyObjectGetProperty(
        @Receiver CallSite callSite,
        Object callReceiver,
        @CallerClassName String consumer
    ) throws Throwable {
        return withEntryPoint(consumer, callSite.getName(), GET_PROPERTY, () -> callSite.callGroovyObjectGetProperty(callReceiver));
    }

    @InterceptJvmCalls
    @CallableKind.StaticMethod(ofClass = ScriptBytecodeAdapter.class)
    public static void intercept_setGroovyObjectProperty(
        Object messageArgument,
        /* Sometimes it's null, we can't rely on it, so we still use @CallerClassName */
        Class<?> senderClass,
        GroovyObject receiver,
        String messageName,
        @CallerClassName String consumer
    ) throws Throwable {
        interceptPropertySetter(messageArgument, receiver, messageName, consumer,
            () -> {
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
        @CallerClassName String consumer
    ) throws Throwable {
        interceptPropertySetter(messageArgument, receiver, messageName, consumer,
            () -> {
                ScriptBytecodeAdapter.setProperty(messageArgument, senderClass, receiver, messageName);
                return null;
            }
        );
    }

    private static void interceptPropertySetter(
        Object messageArgument,
        Object receiver,
        String messageName,
        String consumer,
        ThrowingCallable<Void> setOriginalProperty
    ) throws Throwable {
        if (!(receiver instanceof Closure)) {
            CallInterceptor interceptor = Instrumented.INTERCEPTOR_RESOLVER.resolveCallInterceptor(InterceptScope.writesOfPropertiesNamed(messageName));
            if (interceptor == null) {
                setOriginalProperty.call();
            } else {
                @NonNullApi
                class SetPropertyInvocationImpl extends AbstractInvocation<Object> {
                    public SetPropertyInvocationImpl(Object receiver, Object[] args) {
                        super(receiver, args);
                    }

                    @Override
                    public @Nullable Object callOriginal() throws Throwable {
                        return setOriginalProperty.call();
                    }
                }
                Invocation invocation = new SetPropertyInvocationImpl(receiver, new Object[]{messageArgument});
                interceptor.doIntercept(invocation, consumer);
            }
        } else {
            withEntryPoint(consumer, messageName, SET_PROPERTY, setOriginalProperty);
        }
    }

    @InterceptJvmCalls
    @InstanceMethod
    public static Object intercept_callCurrent(
        @Receiver CallSite callSite,
        GroovyObject receiver,
        @CallerClassName String consumer
    ) throws Throwable {
        return withEntryPoint(consumer, callSite.getName(), INVOKE_METHOD, () -> callSite.callCurrent(receiver));
    }

    @InterceptJvmCalls
    @InstanceMethod
    public static Object intercept_callCurrent(
        @Receiver CallSite callSite,
        GroovyObject receiver,
        Object arg1,
        @CallerClassName String consumer
    ) throws Throwable {
        return withEntryPoint(consumer, callSite.getName(), INVOKE_METHOD, () -> callSite.callCurrent(receiver, arg1));
    }

    @InterceptJvmCalls
    @InstanceMethod
    public static Object intercept_callCurrent(
        @Receiver CallSite callSite,
        GroovyObject receiver,
        Object arg1,
        Object arg2,
        @CallerClassName String consumer
    ) throws Throwable {
        return withEntryPoint(consumer, callSite.getName(), INVOKE_METHOD, () -> callSite.callCurrent(receiver, arg1, arg2));
    }

    @InterceptJvmCalls
    @InstanceMethod
    public static Object intercept_callCurrent(
        @Receiver CallSite callSite,
        GroovyObject receiver,
        Object arg1,
        Object arg2,
        Object arg3,
        @CallerClassName String consumer
    ) throws Throwable {
        return withEntryPoint(consumer, callSite.getName(), INVOKE_METHOD, () -> callSite.callCurrent(receiver, arg1, arg2, arg3));
    }

    @InterceptJvmCalls
    @InstanceMethod
    public static Object intercept_callCurrent(
        @Receiver CallSite callSite,
        GroovyObject receiver,
        Object arg1,
        Object arg2,
        Object arg3,
        Object arg4,
        @CallerClassName String consumer
    ) throws Throwable {
        return withEntryPoint(consumer, callSite.getName(), INVOKE_METHOD, () -> callSite.callCurrent(receiver, arg1, arg2, arg3, arg4));
    }

    @InterceptJvmCalls
    @InstanceMethod
    public static Object intercept_callCurrent(
        @Receiver CallSite callSite,
        GroovyObject receiver,
        Object[] args,
        @CallerClassName String consumer
    ) throws Throwable {
        return withEntryPoint(consumer, callSite.getName(), INVOKE_METHOD, () -> callSite.callCurrent(receiver, args));
    }
}
