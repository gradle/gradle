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

package org.gradle.internal.classpath.intercept;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import groovy.lang.DelegatingMetaClass;
import groovy.lang.GroovyRuntimeException;
import groovy.lang.MetaClass;
import groovy.lang.MetaMethod;
import groovy.lang.Tuple;
import org.codehaus.groovy.reflection.CachedClass;
import org.codehaus.groovy.runtime.MetaClassHelper;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class InterceptorMetaClass extends DelegatingMetaClass {
    private final CallInterceptor interceptor;
    private final Set<InterceptScope> scopes;

    public InterceptorMetaClass(MetaClass delegate, CallInterceptor interceptor) {
        super(delegate);

        Preconditions.checkArgument(!(this.delegate instanceof InterceptorMetaClass), "Installing metaclass for %s twice", delegate);

        this.interceptor = interceptor;
        this.scopes = ImmutableSet.copyOf(interceptor.getInterceptScopes());
    }

    public InterceptorMetaClass(Class<?> delegate, CallInterceptor interceptor) {
        super(delegate);

        Preconditions.checkArgument(!(this.delegate instanceof InterceptorMetaClass), "Installing metaclass for %s twice", delegate);

        this.interceptor = interceptor;
        this.scopes = ImmutableSet.copyOf(interceptor.getInterceptScopes());
    }

    @Override
    public Object invokeMethod(Object object, String methodName, Object arguments) {
        if (!interceptsMethod(methodName)) {
            return super.invokeMethod(object, methodName, arguments);
        }

        if (arguments == null) {
            return invokeMethod(object, methodName, MetaClassHelper.EMPTY_ARRAY);
        }
        if (arguments instanceof Tuple) {
            return invokeMethod(object, methodName, ((Tuple<?>) arguments).toArray());
        }
        if (arguments instanceof Object[]) {
            return invokeMethod(object, methodName, (Object[]) arguments);
        }
        return invokeMethod(object, methodName, new Object[]{arguments});
    }

    @Override
    public Object invokeMethod(Object object, String methodName, Object[] arguments) {
        if (interceptsMethod(methodName)) {
            MetaMethod method = super.getMetaMethod(methodName, MetaClassHelper.convertToTypeArray(arguments));
            if (method != null && interceptsMetaMethod(object, method)) {
                return intercept(new AbstractInvocation<Object>(object, arguments) {
                    @Override
                    public Object callOriginal() {
                        return InterceptorMetaClass.super.invokeMethod(object, methodName, arguments);
                    }
                });
            }
        }
        return super.invokeMethod(object, methodName, arguments);
    }

    @Override
    public Object invokeStaticMethod(Object object, String methodName, Object[] arguments) {
        if (interceptsMethod(methodName)) {
            MetaMethod method = super.getStaticMetaMethod(methodName, MetaClassHelper.convertToTypeArray(arguments));
            if (method != null && interceptsMetaMethod(getTheClass(), method)) {
                return intercept(new AbstractInvocation<Object>(object, arguments) {
                    @Override
                    public Object callOriginal() {
                        return InterceptorMetaClass.super.invokeStaticMethod(object, methodName, arguments);
                    }
                });
            }
        }
        return super.invokeStaticMethod(object, methodName, arguments);
    }

    @Override
    public MetaMethod getStaticMetaMethod(String name, Object[] args) {
        MetaMethod method = super.getStaticMetaMethod(name, args);
        if (method != null && interceptsMethod(name) && interceptsMetaMethod(getTheClass(), method)) {
            return new InterceptorMetaMethod(interceptor, method);
        }
        return method;
    }

    @Override
    public MetaMethod getStaticMetaMethod(String name, Class[] argTypes) {
        return getStaticMetaMethod(name, (Object[]) argTypes);
    }

    @Override
    public MetaMethod getMetaMethod(String name, Object[] args) {
        MetaMethod method = super.getMetaMethod(name, args);
        if (method != null && interceptsMethod(name) && interceptsMetaMethod(getTheClass(), method)) {
            return new InterceptorMetaMethod(interceptor, method);
        }
        return method;
    }

    @Override
    public List<MetaMethod> getMetaMethods() {
        return wrapMethods(super.getMetaMethods());
    }

    @Override
    public List<MetaMethod> getMethods() {
        return wrapMethods(super.getMethods());
    }

    private List<MetaMethod> wrapMethods(List<MetaMethod> methods) {
        return methods.stream().map(method -> {
            if (interceptsMethod(method.getName()) && interceptsMetaMethod(getTheClass(), method)) {
                return new InterceptorMetaMethod(interceptor, method);
            }
            return method;
        }).collect(Collectors.toList());
    }

    @Override
    public Object invokeMethod(Class sender, Object receiver, String methodName, Object[] arguments, boolean isCallToSuper, boolean fromInsideClass) {
        if (!isCallToSuper && !fromInsideClass && interceptsMethod(methodName)) {
            MetaMethod method = super.getMetaMethod(methodName, MetaClassHelper.convertToTypeArray(arguments));
            if (method != null && interceptsMetaMethod(receiver, method)) {
                return intercept(new AbstractInvocation<Object>(receiver, method.coerceArgumentsToClasses(arguments)) {
                    @Override
                    public Object callOriginal() throws Throwable {
                        return InterceptorMetaClass.super.invokeMethod(sender, receiver, methodName, arguments, isCallToSuper, fromInsideClass);
                    }
                });
            }
        }
        return super.invokeMethod(sender, receiver, methodName, arguments, isCallToSuper, fromInsideClass);
    }

    @Override
    public Object invokeMethod(String name, Object args) {
        return super.invokeMethod(name, args);
    }

    private boolean interceptsMethod(String methodName) {
        return scopes.contains(InterceptScope.methodsNamed(methodName));
    }

    private boolean interceptsMetaMethod(Object receiver, MetaMethod method) {
        return interceptsMetaMethod(receiver.getClass(), method);
    }

    private boolean interceptsMetaMethod(Class<?> receiver, MetaMethod method) {
        return ((SignatureAwareCallInterceptor) interceptor).matchesMethodSignature(receiver, method.getNativeParameterTypes(), method.isStatic()) != null;
    }

    private Object intercept(Invocation invocation) {
        try {
            return interceptor.doIntercept(invocation, "TODO");
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable e) {
            throw new GroovyRuntimeException(e);
        }
    }

    private static final class InterceptorMetaMethod extends MetaMethod {
        private final CallInterceptor interceptor;
        private final MetaMethod originalMethod;

        InterceptorMetaMethod(CallInterceptor interceptor, MetaMethod originalMethod) {
            this.interceptor = interceptor;
            this.originalMethod = originalMethod;
        }

        @Override
        public int getModifiers() {
            return originalMethod.getModifiers();
        }

        @Override
        public String getName() {
            return originalMethod.getName();
        }

        @Override
        public Class<?> getReturnType() {
            return originalMethod.getReturnType();
        }

        @Override
        public CachedClass getDeclaringClass() {
            return originalMethod.getDeclaringClass();
        }

        @Override
        public Object invoke(Object object, Object[] arguments) {
            try {
                return interceptor.doIntercept(new AbstractInvocation<Object>(object, originalMethod.coerceArgumentsToClasses(arguments)) {
                    @Override
                    public Object callOriginal() {
                        return originalMethod.invoke(object, arguments);
                    }
                }, "TODO");
            } catch (RuntimeException e) {
                throw e;
            } catch (Throwable e) {
                throw new GroovyRuntimeException(e);
            }
        }

        @Override
        public String toString() {
            return "InterceptorMetaMethod{" + originalMethod + "}";
        }

        @Override
        public Object clone() {
            return new InterceptorMetaMethod(interceptor, (MetaMethod) originalMethod.clone());
        }

        @Override
        public boolean isCacheable() {
            return false;
        }

        @Override
        public Class<?>[] getNativeParameterTypes() {
            return originalMethod.getNativeParameterTypes();
        }

        @Override
        public Object doMethodInvoke(Object object, Object[] argumentArray) {
            return super.doMethodInvoke(object, argumentArray);
        }
    }
}
