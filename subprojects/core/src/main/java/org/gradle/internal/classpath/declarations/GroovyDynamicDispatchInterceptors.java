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

import groovy.lang.GroovyObject;
import org.codehaus.groovy.runtime.ScriptBytecodeAdapter;
import org.gradle.api.NonNullApi;
import org.gradle.internal.classpath.Instrumented;
import org.gradle.internal.classpath.InstrumentedClosuresHelper;
import org.gradle.internal.classpath.InstrumentedGroovyCallsTracker;
import org.gradle.internal.classpath.intercept.AbstractInvocation;
import org.gradle.internal.classpath.intercept.CallInterceptor;
import org.gradle.internal.classpath.intercept.InterceptScope;
import org.gradle.internal.classpath.intercept.Invocation;
import org.gradle.internal.instrumentation.api.annotations.CallableKind;
import org.gradle.internal.instrumentation.api.annotations.InterceptJvmCalls;
import org.gradle.internal.instrumentation.api.annotations.ParameterKind.CallerClassName;
import org.gradle.internal.instrumentation.api.annotations.SpecificJvmCallInterceptors;
import org.gradle.internal.instrumentation.api.declarations.InterceptorDeclaration;

import javax.annotation.Nullable;

import static org.gradle.internal.classpath.InstrumentedGroovyCallsTracker.CallKind.SET_PROPERTY;
import static org.gradle.internal.classpath.InstrumentedGroovyCallsTracker.withEntryPoint;

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
        @CallerClassName String consumer
    ) throws Throwable {
        if (!Instrumented.INTERCEPTOR_RESOLVER.isAwareOfCallSiteName(messageName)) {
            ScriptBytecodeAdapter.setGroovyObjectProperty(messageArgument, senderClass, receiver, messageName);
            return;
        }

        CallInterceptor interceptor = Instrumented.INTERCEPTOR_RESOLVER.resolveCallInterceptor(InterceptScope.writesOfPropertiesNamed(messageName));
        InstrumentedGroovyCallsTracker.ThrowingCallable<Object> setOriginalProperty = () -> {
            ScriptBytecodeAdapter.setGroovyObjectProperty(messageArgument, senderClass, receiver, messageName);
            return null;
        };
        if (interceptor == null) {
            InstrumentedClosuresHelper.INSTANCE.hitInstrumentedDynamicCall();
            withEntryPoint(consumer, messageName, SET_PROPERTY, setOriginalProperty);
        } else {
            Invocation invocation = new SetPropertyInvocationImpl(receiver, new Object[]{messageArgument}, consumer, messageName, setOriginalProperty);
            interceptor.doIntercept(invocation, consumer);
        }
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
        CallInterceptor interceptor = Instrumented.INTERCEPTOR_RESOLVER.resolveCallInterceptor(InterceptScope.writesOfPropertiesNamed(messageName));
        if (interceptor == null) {
            ScriptBytecodeAdapter.setProperty(messageArgument, senderClass, receiver, messageName);
        } else {
            Invocation invocation = new SetPropertyInvocationImpl(receiver, new Object[]{messageArgument}, consumer, messageName, () -> {
                ScriptBytecodeAdapter.setProperty(messageArgument, senderClass, receiver, messageName);
                return null;
            });
            interceptor.doIntercept(invocation, consumer);
        }
    }

    @NonNullApi
    private static class SetPropertyInvocationImpl extends AbstractInvocation<Object> {
        private final String consumer;
        private final String messageName;
        private final InstrumentedGroovyCallsTracker.ThrowingCallable<?> setOriginalProperty;

        public SetPropertyInvocationImpl(Object receiver, Object[] args, String consumer, String messageName, InstrumentedGroovyCallsTracker.ThrowingCallable<?> setOriginalProperty) {
            super(receiver, args);
            this.consumer = consumer;
            this.messageName = messageName;
            this.setOriginalProperty = setOriginalProperty;
        }

        @Override
        public @Nullable Object callOriginal() throws Throwable {
            // the interceptor did not match the call, but it can resolve
            // dynamically to a different receiver under the hood, so track it:
            InstrumentedClosuresHelper.INSTANCE.hitInstrumentedDynamicCall();
            return withEntryPoint(consumer, messageName, SET_PROPERTY, setOriginalProperty);
        }
    }
}
