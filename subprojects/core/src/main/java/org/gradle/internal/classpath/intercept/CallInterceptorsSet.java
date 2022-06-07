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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Holds a collection of interceptors and can decorate a Groovy CallSite if it is within a scope of a registered interceptor.
 */
public class CallInterceptorsSet {
    private final Map<InterceptScope, CallInterceptor> interceptors = new HashMap<>();
    private final Set<String> interceptedCallSiteNames = new HashSet<>();

    /**
     * Creates the interceptor set out of provided interceptors.
     */
    public CallInterceptorsSet(CallInterceptor... interceptors) {
        for (CallInterceptor interceptor : interceptors) {
            addInterceptor(interceptor);
        }
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
            if (receiver instanceof Class) {
                Class<?> constructorClass = (Class<?>) receiver;
                CallInterceptor interceptor = interceptors.get(InterceptScope.constructorsOf(constructorClass));
                if (interceptor != null) {
                    return interceptor.doIntercept(new AbstractInvocation<Object>(receiver, args) {
                        @Override
                        public Object callOriginal() throws Throwable {
                            return DecoratingCallSite.super.callConstructor(receiver, args);
                        }
                    }, array.owner.getName());
                }
            }
            return super.callConstructor(receiver, args);
        }
    }
}
