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

import org.codehaus.groovy.runtime.callsite.AbstractCallSite;
import org.codehaus.groovy.runtime.callsite.CallSite;
import org.codehaus.groovy.vmplugin.v8.CacheableCallSite;
import org.gradle.api.GradleException;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Holds a collection of interceptors and can decorate a Groovy CallSite if it is within a scope of a registered interceptor.
 */
public class CallInterceptorsSet {
    private final Map<InterceptScope, CallInterceptor> interceptors = new HashMap<>();
    private final Set<String> interceptedCallSiteNames = new HashSet<>();

    // There is no information about the type returned by the constructor invocation in the bytecode, so the
    // dynamic dispatch has to happen somewhere. Wrapping the dispatch logic into the CallInterceptor allows
    // to reuse the common MethodHandle decoration routine in maybeDecorateIndyCallSite instead of using a
    // dedicated MethodHandle decorator method just for constructors.
    private final CallInterceptor dispatchingConstructorInterceptor = new CallInterceptor() {
        @Override
        protected Object doIntercept(Invocation invocation, String consumer) throws Throwable {
            Object receiver = invocation.getReceiver();
            if (receiver instanceof Class) {
                CallInterceptor realConstructorInterceptor = interceptors.get(InterceptScope.constructorsOf((Class<?>) receiver));
                if (realConstructorInterceptor != null) {
                    return realConstructorInterceptor.doIntercept(invocation, consumer);
                }
            }
            return invocation.callOriginal();
        }
    };

    /**
     * Creates the interceptor set, collecting the interceptors from the stream.
     */
    public CallInterceptorsSet(Stream<CallInterceptor> interceptors) {
        interceptors.forEach(this::addInterceptor);
    }

    private void addInterceptor(CallInterceptor interceptor) {
        for (InterceptScope scope : interceptor.getInterceptScopes()) {
            CallInterceptor oldInterceptor = interceptors.put(scope, interceptor);
            if (oldInterceptor != null) {
                throw new IllegalArgumentException("Interceptor " + interceptor +
                    " attempted to overwrite already registered " + oldInterceptor +
                    " in the scope " + scope);
            }
            interceptedCallSiteNames.add(scope.getCallSiteName());
        }
    }

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
    public CallSite maybeDecorateGroovyCallSite(CallSite originalCallSite) {
        if (shouldDecorate(originalCallSite)) {
            return new DecoratingCallSite(originalCallSite);
        }
        return originalCallSite;
    }

    private boolean shouldDecorate(CallSite callSite) {
        return interceptedCallSiteNames.contains(callSite.getName());
    }

    private class DecoratingCallSite extends AbstractCallSite {
        public DecoratingCallSite(CallSite prev) {
            super(prev);
        }

        @Override
        public Object call(Object receiver, Object[] args) throws Throwable {
            CallInterceptor interceptor = interceptors.get(InterceptScope.methodsNamed(getName()));
            if (interceptor != null) {
                return interceptor.doIntercept(new AbstractInvocation<Object>(receiver, args) {
                    @Override
                    public Object callOriginal() throws Throwable {
                        return DecoratingCallSite.super.call(receiver, args);
                    }
                }, array.owner.getName());
            }
            return super.call(receiver, args);
        }

        @Override
        public Object callGetProperty(Object receiver) throws Throwable {
            CallInterceptor interceptor = interceptors.get(InterceptScope.readsOfPropertiesNamed(getName()));
            if (interceptor != null) {
                return interceptor.doIntercept(new AbstractInvocation<Object>(receiver, new Object[0]) {
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
            CallInterceptor interceptor = interceptors.get(InterceptScope.methodsNamed(getName()));
            if (interceptor != null) {
                return interceptor.doIntercept(new AbstractInvocation<Class<?>>(receiver, args) {
                    @Override
                    public Object callOriginal() throws Throwable {
                        return DecoratingCallSite.super.callStatic(receiver, args);
                    }
                }, array.owner.getName());
            }
            return super.callStatic(receiver, args);
        }

        @Override
        public Object callConstructor(Object receiver, Object[] args) throws Throwable {
            return dispatchingConstructorInterceptor.doIntercept(new AbstractInvocation<Object>(receiver, args) {
                @Override
                public Object callOriginal() throws Throwable {
                    return DecoratingCallSite.super.callConstructor(receiver, args);
                }
            }, array.owner.getName());
        }
    }

}
