/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.classpath.intercept;

import groovy.lang.GroovyObject;
import org.codehaus.groovy.runtime.callsite.AbstractCallSite;
import org.codehaus.groovy.runtime.callsite.CallSite;
import org.codehaus.groovy.vmplugin.v8.CacheableCallSite;
import org.gradle.api.GradleException;
import org.gradle.api.NonNullApi;
import org.gradle.internal.classpath.InstrumentedClosuresHelper;
import org.gradle.internal.classpath.InstrumentedGroovyCallsTracker;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.gradle.internal.classpath.InstrumentedGroovyCallsHelper.withEntryPoint;
import static org.gradle.internal.classpath.InstrumentedGroovyCallsTracker.CallKind.GET_PROPERTY;
import static org.gradle.internal.classpath.InstrumentedGroovyCallsTracker.CallKind.INVOKE_METHOD;

/**
 * Holds a collection of interceptors and can decorate a Groovy CallSite if it is within a scope of a registered interceptor.
 */
@NonNullApi
public class DefaultCallSiteDecorator implements CallSiteDecorator, CallInterceptorResolver {
    private final Map<InterceptScope, CallInterceptor> interceptors = new HashMap<>();
    private final Set<String> interceptedCallSiteNames = new HashSet<>();

    // There is no information about the type returned by the constructor invocation in the bytecode, so the
    // dynamic dispatch has to happen somewhere. Wrapping the dispatch logic into the CallInterceptor allows
    // to reuse the common MethodHandle decoration routine in maybeDecorateIndyCallSite instead of using a
    // dedicated MethodHandle decorator method just for constructors.
    private final CallInterceptor dispatchingConstructorInterceptor = new AbstractCallInterceptor() {
        @Override
        public Object intercept(Invocation invocation, String consumer) throws Throwable {
            Object receiver = invocation.getReceiver();
            if (receiver instanceof Class) {
                CallInterceptor realConstructorInterceptor = interceptors.get(InterceptScope.constructorsOf((Class<?>) receiver));
                if (realConstructorInterceptor != null) {
                    return realConstructorInterceptor.intercept(invocation, consumer);
                }
            }
            return invocation.callOriginal();
        }
    };

    /**
     * Creates the interceptor set, collecting the interceptors from the stream.
     */
    public DefaultCallSiteDecorator(List<CallInterceptor> callInterceptors) {
        callInterceptors.forEach(this::addInterceptor);
    }

    private void addInterceptor(CallInterceptor interceptor) {
        for (InterceptScope scope : interceptor.getInterceptScopes()) {
            interceptors.compute(scope, (__, previous) -> previous == null ? interceptor : new CompositeCallInterceptor(previous, interceptor));
            interceptedCallSiteNames.add(scope.getCallSiteName());
        }
    }

    @Override
    public java.lang.invoke.CallSite maybeDecorateIndyCallSite(java.lang.invoke.CallSite originalCallSite, MethodHandles.Lookup caller, String callType, String name, int flags) {
        CacheableCallSite ccs = toGroovyCacheableCallSite(originalCallSite);
        switch (callType) {
            case "invoke":
                maybeApplyInterceptor(ccs, caller, flags, interceptors.get(InterceptScope.methodsNamed(name)));
                break;
            case "getProperty":
                maybeApplyInterceptor(ccs, caller, flags, interceptors.get(InterceptScope.readsOfPropertiesNamed(name)));
                break;
            case "init":
                maybeApplyInterceptor(ccs, caller, flags, dispatchingConstructorInterceptor);
                break;
        }
        return ccs;
    }

    private static void maybeApplyInterceptor(CacheableCallSite cs, MethodHandles.Lookup caller, int flags, @Nullable CallInterceptor interceptor) {
        if (interceptor == null) {
            return;
        }
        MethodHandle defaultTarget = interceptor.decorateMethodHandle(cs.getDefaultTarget(), caller, flags);
        cs.setTarget(defaultTarget);
        cs.setDefaultTarget(defaultTarget);
        cs.setFallbackTarget(interceptor.decorateMethodHandle(cs.getFallbackTarget(), caller, flags));
    }

    private static CacheableCallSite toGroovyCacheableCallSite(java.lang.invoke.CallSite cs) {
        if (!(cs instanceof CacheableCallSite)) {
            throw new GradleException("Groovy produced unrecognized call site type of " + cs.getClass());
        }
        return (CacheableCallSite) cs;
    }

    /**
     * Decorates a Groovy {@link CallSite} if the interceptor is registered for the method/property/constructor.
     * The returned CallSite instance will perform the actual interception.
     *
     * @param originalCallSite the CallSite to decorate
     * @return the new CallSite capable of intercepting calls or the original CallSite if interception not neccessary.
     */
    @Override
    public CallSite maybeDecorateGroovyCallSite(CallSite originalCallSite) {
        if (shouldDecorate(originalCallSite)) {
            return new DecoratingCallSite(originalCallSite);
        }
        return originalCallSite;
    }

    private boolean shouldDecorate(CallSite callSite) {
        return interceptedCallSiteNames.contains(callSite.getName());
    }

    @Override
    @Nullable
    public CallInterceptor resolveCallInterceptor(InterceptScope scope) {
        return interceptors.get(scope);
    }

    @Override
    public boolean isAwareOfCallSiteName(String name) {
        return interceptedCallSiteNames.contains(name);
    }

    private class DecoratingCallSite extends AbstractCallSite {
        private @Nullable CallSite groovyDefaultCallSite = null;

        public DecoratingCallSite(CallSite prev) {
            super(prev);
        }

        @Override
        public Object call(Object receiver, Object[] args) throws Throwable {
            CallInterceptor interceptor = resolveCallInterceptor(InterceptScope.methodsNamed(getName()));
            if (interceptor != null) {
                return interceptor.intercept(new AbstractInvocation<Object>(receiver, args) {
                    @Override
                    public Object callOriginal() throws Throwable {
                        return DecoratingCallSite.super.call(receiver, args);
                    }
                }, callSiteOwnerClassName());
            }
            return super.call(receiver, args);
        }

        @Override
        public Object callGetProperty(Object receiver) throws Throwable {
            CallInterceptor interceptor = resolveCallInterceptor(InterceptScope.readsOfPropertiesNamed(getName()));
            if (interceptor != null) {
                return interceptor.intercept(new AbstractInvocation<Object>(receiver, new Object[0]) {
                    @Override
                    public Object callOriginal() throws Throwable {
                        return DecoratingCallSite.super.callGetProperty(receiver);
                    }
                }, array.owner.getName());
            }
            return super.callGetProperty(receiver);
        }

        @Override
        public Object callStatic(Class receiver, Object[] args) throws Throwable {
            CallInterceptor interceptor = resolveCallInterceptor(InterceptScope.methodsNamed(getName()));
            if (interceptor != null) {
                return interceptor.intercept(new AbstractInvocation<Class<?>>(receiver, args) {
                    @Override
                    public Object callOriginal() throws Throwable {
                        return DecoratingCallSite.super.callStatic(receiver, args);
                    }
                }, callSiteOwnerClassName());
            }
            return super.callStatic(receiver, args);
        }

        @Override
        public Object callConstructor(Object receiver, Object[] args) throws Throwable {
            return dispatchingConstructorInterceptor.intercept(new AbstractInvocation<Object>(receiver, args) {
                @Override
                public Object callOriginal() throws Throwable {
                    return DecoratingCallSite.super.callConstructor(receiver, args);
                }
            }, callSiteOwnerClassName());
        }

        private @Nullable Object maybeInstrumentedDynamicCall(
            CallStrategy callStrategy,
            Object receiver,
            @Nullable Object[] args
        ) throws Throwable {
            if (interceptedCallSiteNames.contains(getName())) {
                InstrumentedGroovyCallsTracker.CallKind kind = callStrategy == CallStrategy.CALL_CURRENT ? INVOKE_METHOD : GET_PROPERTY;
                InstrumentedClosuresHelper.INSTANCE.hitInstrumentedDynamicCall();
                return withEntryPoint(callSiteOwnerClassName(), getName(), kind, () -> invokeDefaultGroovyCallSiteImplementation(receiver, args, callStrategy));
            } else {
                // Note: this effectively removes the reference to the decorated call site from the call site array, thus not allowing us
                // to change the decision and intercept the call anymore. For now, we are fine with that.
                if (callStrategy == CallStrategy.CALL_CURRENT) {
                    return super.callCurrent((GroovyObject) receiver, args);
                } else {
                    return super.callGroovyObjectGetProperty(receiver);
                }
            }
        }

        private Object invokeDefaultGroovyCallSiteImplementation(Object receiver, @Nullable Object[] args, CallStrategy callStrategy) throws Throwable {
            assert groovyDefaultCallSite != this;
            switch (callStrategy) {
                case CALL_CURRENT:
                    if (groovyDefaultCallSite != null) {
                        return groovyDefaultCallSite.callCurrent((GroovyObject) receiver, args);
                    } else {
                        try {
                            return super.callCurrent((GroovyObject) receiver, args);
                        } finally {
                            restoreCallSiteArrayEntry();
                        }
                    }
                case CALL_GROOVY_OBJECT_GET_PROPERTY:
                    if (groovyDefaultCallSite != null) {
                        return groovyDefaultCallSite.callGroovyObjectGetProperty(receiver);
                    } else {
                        try {
                            return super.callGroovyObjectGetProperty(receiver);
                        } finally {
                            restoreCallSiteArrayEntry();
                        }
                    }
                default:
                    throw new IllegalArgumentException("Unexpected callStrategy " + callStrategy);
            }
        }

        /**
         * The default Groovy implementation replaces the entry in the call site array with what it creates based on the call kind. <p>
         *
         * For example, see this code path: <p>
         * <ul>
         *     <li> {@link AbstractCallSite#callCurrent(GroovyObject, Object[])}
         *     <li> {@link org.codehaus.groovy.runtime.callsite.CallSiteArray#defaultCallCurrent}
         *     <li> {@link org.codehaus.groovy.runtime.callsite.CallSiteArray#createCallCurrentSite}
         *     <li> {@link org.codehaus.groovy.runtime.callsite.CallSiteArray#replaceCallSite}
         * </ul>
         *
         * Because of it doing so, our decorated call site is removed from the dynamic invocation code path, and we lose the ability to track dynamic call entry points. <p>
         *
         * To fix that, we store the optimized call site that the Groovy runtime created (and we delegate to it in subsequent calls), and we put a reference to our
         * decorated call site back into the call site array.
         */
        private void restoreCallSiteArrayEntry() {
            CallSite callSiteInArrayAfterCall = array.array[index];
            if (callSiteInArrayAfterCall != this) {
                groovyDefaultCallSite = callSiteInArrayAfterCall;
            }
            array.array[index] = this;
        }

        @Override
        public @Nullable Object callGroovyObjectGetProperty(Object receiver) throws Throwable {
            return maybeInstrumentedDynamicCall(CallStrategy.CALL_GROOVY_OBJECT_GET_PROPERTY, receiver, null);
        }

        @Override
        public @Nullable Object callCurrent(GroovyObject receiver, Object[] args) throws Throwable {
            return maybeInstrumentedDynamicCall(CallStrategy.CALL_CURRENT, receiver, args);
        }

        private String callSiteOwnerClassName() {
            return array.owner.getName();
        }
    }

    @NonNullApi
    enum CallStrategy {
        CALL_CURRENT, CALL_GROOVY_OBJECT_GET_PROPERTY
    }
}
