/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.tooling.internal.adapter;

import org.gradle.api.Action;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.reflect.DirectInstantiator;
import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.internal.Exceptions;
import org.gradle.tooling.model.internal.ImmutableDomainObjectSet;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Adapts some source object to some target view type.
 */
public class ProtocolToModelAdapter implements Serializable {
    private static final MethodInvoker NO_OP_HANDLER = new NoOpMethodInvoker();
    private static final Action<SourceObjectMapping> NO_OP_MAPPER = new NoOpMapping();
    private static final TargetTypeProvider IDENTITY_TYPE_PROVIDER = new TargetTypeProvider() {
        public <T> Class<? extends T> getTargetType(Class<T> initialTargetType, Object protocolObject) {
            return initialTargetType;
        }
    };
    private static final Object[] EMPTY = new Object[0];
    private static final Pattern IS_SUPPORT_METHOD = Pattern.compile("is(\\w+)Supported");
    private static final Pattern GETTER_METHOD = Pattern.compile("get(\\w+)");
    private static final Pattern IS_METHOD = Pattern.compile("is(\\w+)");
    private final TargetTypeProvider targetTypeProvider;
    private final CollectionMapper collectionMapper = new CollectionMapper();

    public ProtocolToModelAdapter() {
        this(IDENTITY_TYPE_PROVIDER);
    }

    public ProtocolToModelAdapter(TargetTypeProvider targetTypeProvider) {
        this.targetTypeProvider = targetTypeProvider;
    }

    /**
     * Adapts the source object to a view object.
     */
    public <T, S> T adapt(Class<T> targetType, S sourceObject) {
        return adapt(targetType, sourceObject, NO_OP_MAPPER);
    }

    /**
     * Adapts the source object to a view object.
     *
     * @param mixInClass A bean that provides implementations for methods of the target type. If this bean implements the given method, it is preferred over the source object's implementation.
     */
    public <T, S> T adapt(Class<T> targetType, final S sourceObject, final Class<?> mixInClass) {
        return adapt(targetType, sourceObject, new Action<SourceObjectMapping>() {
            public void execute(SourceObjectMapping mapping) {
                if (mapping.getSourceObject() == sourceObject) {
                    mapping.mixIn(mixInClass);
                }
            }
        });
    }

    /**
     * Adapts the source object to a view object.
     *
     * @param mapper An action that is invoked for each source object in the graph that is to be adapted. The action can influence how the source object is adapted via the provided
     * {@link SourceObjectMapping}.
     */
    public <T, S> T adapt(Class<T> targetType, S sourceObject, Action<? super SourceObjectMapping> mapper) {
        if (sourceObject == null) {
            return null;
        }
        Class<? extends T> wrapperType = targetTypeProvider.getTargetType(targetType, sourceObject);
        DefaultSourceObjectMapping mapping = new DefaultSourceObjectMapping(sourceObject, targetType, wrapperType);
        mapper.execute(mapping);
        wrapperType = mapping.wrapperType.asSubclass(targetType);
        if (wrapperType.isInstance(sourceObject)) {
            return wrapperType.cast(sourceObject);
        }
        MethodInvoker overrideMethodInvoker = mapping.overrideInvoker;
        MixInMethodInvoker mixInMethodInvoker = null;
        if (mapping.mixInType != null) {
            mixInMethodInvoker = new MixInMethodInvoker(mapping.mixInType, new AdaptingMethodInvoker(mapper, new ReflectionMethodInvoker()));
            overrideMethodInvoker = mixInMethodInvoker;
        }
        Object proxy = Proxy.newProxyInstance(wrapperType.getClassLoader(), new Class<?>[]{wrapperType}, new InvocationHandlerImpl(sourceObject, overrideMethodInvoker, mapper));
        if (mixInMethodInvoker != null) {
            mixInMethodInvoker.setProxy(proxy);
        }
        return wrapperType.cast(proxy);
    }

    /**
     * Unpacks the source object from a given view object.
     */
    public Object unpack(Object viewObject) {
        if (!Proxy.isProxyClass(viewObject.getClass()) || !(Proxy.getInvocationHandler(viewObject) instanceof InvocationHandlerImpl)) {
            throw new IllegalArgumentException("The given object is not a view object");
        }
        InvocationHandlerImpl handler = (InvocationHandlerImpl) Proxy.getInvocationHandler(viewObject);
        return handler.delegate;
    }

    private static class DefaultSourceObjectMapping implements SourceObjectMapping {
        private final Object protocolObject;
        private final Class<?> targetType;
        private Class<?> wrapperType;
        private Class<?> mixInType;
        private MethodInvoker overrideInvoker = NO_OP_HANDLER;

        public DefaultSourceObjectMapping(Object protocolObject, Class<?> targetType, Class<?> wrapperType) {
            this.protocolObject = protocolObject;
            this.targetType = targetType;
            this.wrapperType = wrapperType;
        }

        public Object getSourceObject() {
            return protocolObject;
        }

        public Class<?> getTargetType() {
            return targetType;
        }

        public void mapToType(Class<?> type) {
            if (!targetType.isAssignableFrom(type)) {
                throw new IllegalArgumentException(String.format("requested wrapper type '%s' is not assignable to target type '%s'.", type.getSimpleName(), targetType.getSimpleName()));
            }
            wrapperType = type;
        }

        public void mixIn(Class<?> mixInBeanType) {
            if (mixInType != null) {
                throw new UnsupportedOperationException("Mixing in multiple beans is currently not supported.");
            }
            mixInType = mixInBeanType;
        }

        public void mixIn(MethodInvoker invoker) {
            if (overrideInvoker != NO_OP_HANDLER) {
                throw new UnsupportedOperationException("Mixing in multiple invokers is currently not supported.");
            }
            overrideInvoker = invoker;
        }
    }

    private static class NoOpMapping implements Action<SourceObjectMapping>, Serializable {
        public void execute(SourceObjectMapping mapping) {
        }
    }

    private class InvocationHandlerImpl implements InvocationHandler, Serializable {
        private final Object delegate;
        private final MethodInvoker overrideMethodInvoker;
        private final Action<? super SourceObjectMapping> mapper;
        private transient Method equalsMethod;
        private transient Method hashCodeMethod;
        private transient MethodInvoker invoker;

        public InvocationHandlerImpl(Object delegate, MethodInvoker overrideMethodInvoker, Action<? super SourceObjectMapping> mapper) {
            this.delegate = delegate;
            this.overrideMethodInvoker = overrideMethodInvoker;
            this.mapper = mapper;
            setup();
        }

        private void readObject(java.io.ObjectInputStream in)
             throws IOException, ClassNotFoundException {
            in.defaultReadObject();
            setup();
        }

        private void setup() {
            invoker = new SupportedPropertyInvoker(
                    new SafeMethodInvoker(
                            new PropertyCachingMethodInvoker(
                                    new AdaptingMethodInvoker(mapper,
                                            new ChainedMethodInvoker(
                                                    overrideMethodInvoker,
                                                    new ReflectionMethodInvoker())))));
            try {
                equalsMethod = Object.class.getMethod("equals", Object.class);
                hashCodeMethod = Object.class.getMethod("hashCode");
            } catch (NoSuchMethodException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            }
            if (o == null || o.getClass() != getClass()) {
                return false;
            }

            InvocationHandlerImpl other = (InvocationHandlerImpl) o;
            return delegate.equals(other.delegate);
        }

        @Override
        public int hashCode() {
            return delegate.hashCode();
        }

        public Object invoke(Object target, Method method, Object[] params) throws Throwable {
            if (method.equals(equalsMethod)) {
                Object param = params[0];
                if (param == null || !Proxy.isProxyClass(param.getClass())) {
                    return false;
                }
                InvocationHandler other = Proxy.getInvocationHandler(param);
                return equals(other);
            } else if (method.equals(hashCodeMethod)) {
                return hashCode();
            }

            MethodInvocation invocation = new MethodInvocation(method.getName(), method.getReturnType(), method.getGenericReturnType(), method.getParameterTypes(), delegate, params);
            invoker.invoke(invocation);
            if (!invocation.found()) {
                String methodName = method.getDeclaringClass().getSimpleName() + "." + method.getName() + "()";
                throw Exceptions.unsupportedMethod(methodName);
            }
            return invocation.getResult();
        }
    }

    private static class ChainedMethodInvoker implements MethodInvoker {
        private final MethodInvoker[] invokers;

        private ChainedMethodInvoker(MethodInvoker... invokers) {
            this.invokers = invokers;
        }

        public void invoke(MethodInvocation method) throws Throwable {
            for (int i = 0; !method.found() && i < invokers.length; i++) {
                MethodInvoker invoker = invokers[i];
                invoker.invoke(method);
            }
        }
    }

    private class AdaptingMethodInvoker implements MethodInvoker {
        private final Action<? super SourceObjectMapping> mapping;
        private final MethodInvoker next;

        private AdaptingMethodInvoker(Action<? super SourceObjectMapping> mapping, MethodInvoker next) {
            this.mapping = mapping;
            this.next = next;
        }

        public void invoke(MethodInvocation invocation) throws Throwable {
            next.invoke(invocation);
            if (invocation.found() && invocation.getResult() != null) {
                invocation.setResult(convert(invocation.getResult(), invocation.getGenericReturnType()));
            }
        }

        private Object convert(Object value, Type targetType) {
            if (targetType instanceof ParameterizedType) {
                ParameterizedType parameterizedTargetType = (ParameterizedType) targetType;
                if (parameterizedTargetType.getRawType() instanceof Class) {
                    Class<?> rawClass = (Class<?>) parameterizedTargetType.getRawType();
                    if (Iterable.class.isAssignableFrom(rawClass)) {
                        Type targetElementType = getElementType(parameterizedTargetType, 0);
                        Collection<Object> convertedElements = collectionMapper.createEmptyCollection(rawClass);
                        for (Object element : (Iterable<?>) value) {
                            convertedElements.add(convert(element, targetElementType));
                        }
                        if (rawClass.equals(DomainObjectSet.class)) {
                            return new ImmutableDomainObjectSet(convertedElements);
                        } else {
                            return convertedElements;
                        }
                    }
                    if (Map.class.isAssignableFrom(rawClass)) {
                        Type targetKeyType = getElementType(parameterizedTargetType, 0);
                        Type targetValueType = getElementType(parameterizedTargetType, 1);
                        Map<Object, Object> convertedElements = collectionMapper.createEmptyMap(rawClass);
                        for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                            convertedElements.put(convert(entry.getKey(), targetKeyType), convert(entry.getValue(), targetValueType));
                        }
                        return convertedElements;
                    }
                }
            }
            if (targetType instanceof Class) {
                if (((Class) targetType).isPrimitive()) {
                    return value;
                }
                return adapt((Class) targetType, value, mapping);
            }
            throw new UnsupportedOperationException(String.format("Cannot convert object of %s to %s.", value.getClass(), targetType));
        }

        private Type getElementType(ParameterizedType type, int index) {
            Type elementType = type.getActualTypeArguments()[index];
            if (elementType instanceof WildcardType) {
                WildcardType wildcardType = (WildcardType) elementType;
                return wildcardType.getUpperBounds()[0];
            }
            return elementType;
        }
    }

    private class ReflectionMethodInvoker implements MethodInvoker {
        public void invoke(MethodInvocation invocation) throws Throwable {
            // TODO - cache method lookup
            Method targetMethod = locateMethod(invocation);
            if (targetMethod == null) {
                return;
            }

            Object returnValue;
            try {
                returnValue = targetMethod.invoke(invocation.getDelegate(), invocation.getParameters());
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }

            invocation.setResult(returnValue);
        }

        private Method locateMethod(MethodInvocation invocation) {
            Class<?> sourceClass = invocation.getDelegate().getClass();
            Method match;
            try {
                match = sourceClass.getMethod(invocation.getName(), invocation.getParameterTypes());
            } catch (NoSuchMethodException e) {
                return null;
            }

            LinkedList<Class<?>> queue = new LinkedList<Class<?>>();
            queue.add(sourceClass);
            while (!queue.isEmpty()) {
                Class<?> c = queue.removeFirst();
                try {
                    match = c.getMethod(invocation.getName(), invocation.getParameterTypes());
                } catch (NoSuchMethodException e) {
                    // ignore
                }
                for (Class<?> interfaceType : c.getInterfaces()) {
                    queue.addFirst(interfaceType);
                }
                if (c.getSuperclass() != null) {
                    queue.addFirst(c.getSuperclass());
                }
            }
            match.setAccessible(true);
            return match;
        }
    }

    private static class PropertyCachingMethodInvoker implements MethodInvoker {
        private final Map<String, Object> properties = new HashMap<String, Object>();
        private final Set<String> unknown = new HashSet<String>();
        private final MethodInvoker next;

        private PropertyCachingMethodInvoker(MethodInvoker next) {
            this.next = next;
        }

        public void invoke(MethodInvocation method) throws Throwable {
            if ((GETTER_METHOD.matcher(method.getName()).matches() || IS_METHOD.matcher(method.getName()).matches()) && method.getParameterTypes().length == 0) {
                if (properties.containsKey(method.getName())) {
                    method.setResult(properties.get(method.getName()));
                    return;
                }
                if (unknown.contains(method.getName())) {
                    return;
                }

                Object value;
                next.invoke(method);
                if (!method.found()) {
                    unknown.add(method.getName());
                    return;
                }
                value = method.getResult();
                properties.put(method.getName(), value);
                return;
            }

            next.invoke(method);
        }
    }

    private static class SafeMethodInvoker implements MethodInvoker {
        private final MethodInvoker next;

        private SafeMethodInvoker(MethodInvoker next) {
            this.next = next;
        }

        public void invoke(MethodInvocation invocation) throws Throwable {
            next.invoke(invocation);
            if (invocation.found()) {
                return;
            }

            boolean getter = GETTER_METHOD.matcher(invocation.getName()).matches();
            if (!getter || invocation.getParameterTypes().length != 1) {
                return;
            }

            MethodInvocation getterInvocation = new MethodInvocation(invocation.getName(), invocation.getReturnType(), invocation.getGenericReturnType(), new Class[0], invocation.getDelegate(), EMPTY);
            next.invoke(getterInvocation);
            if (getterInvocation.found() && getterInvocation.getResult() != null) {
                invocation.setResult(getterInvocation.getResult());
            } else {
                invocation.setResult(invocation.getParameters()[0]);
            }
        }
    }

    private static class SupportedPropertyInvoker implements MethodInvoker {
        private final MethodInvoker next;

        private SupportedPropertyInvoker(MethodInvoker next) {
            this.next = next;
        }

        public void invoke(MethodInvocation invocation) throws Throwable {
            Matcher matcher = IS_SUPPORT_METHOD.matcher(invocation.getName());
            if (!matcher.matches()) {
                next.invoke(invocation);
                return;
            }

            String getterName = String.format("get%s", matcher.group(1));
            MethodInvocation getterInvocation = new MethodInvocation(getterName, invocation.getReturnType(), invocation.getGenericReturnType(), new Class[0], invocation.getDelegate(), EMPTY);
            next.invoke(getterInvocation);
            invocation.setResult(getterInvocation.found());
        }
    }

    private static class MixInMethodInvoker implements MethodInvoker {
        private Object proxy;
        private Object instance;
        private final Class<?> mixInClass;
        private final MethodInvoker next;
        private final ThreadLocal<MethodInvocation> current = new ThreadLocal<MethodInvocation>();

        public MixInMethodInvoker(Class<?> mixInClass, MethodInvoker next) {
            this.mixInClass = mixInClass;
            this.next = next;
        }

        public void invoke(MethodInvocation invocation) throws Throwable {
            if (current.get() != null) {
                // Already invoking a method on the mix-in
                return;
            }

            if (instance == null) {
                instance = new DirectInstantiator().newInstance(mixInClass, proxy);
            }
            MethodInvocation beanInvocation = new MethodInvocation(invocation.getName(), invocation.getReturnType(), invocation.getGenericReturnType(), invocation.getParameterTypes(), instance, invocation.getParameters());
            current.set(beanInvocation);
            try {
                next.invoke(beanInvocation);
            } finally {
                current.set(null);
            }
            if (beanInvocation.found()) {
                invocation.setResult(beanInvocation.getResult());
            }
        }

        public void setProxy(Object proxy) {
            this.proxy = proxy;
        }

        public Object getProxy() {
            return proxy;
        }
    }
}
