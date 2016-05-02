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

import com.google.common.base.Optional;
import org.gradle.api.Action;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.reflect.DirectInstantiator;
import org.gradle.internal.typeconversion.EnumFromCharSequenceNotationParser;
import org.gradle.internal.typeconversion.NotationConverterToNotationParserAdapter;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.internal.typeconversion.TypeConversionException;
import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.internal.Exceptions;
import org.gradle.tooling.model.internal.ImmutableDomainObjectSet;

import java.io.IOException;
import java.io.Serializable;
import java.lang.ref.SoftReference;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
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
    private final TargetTypeProvider targetTypeProvider;
    private final CollectionMapper collectionMapper = new CollectionMapper();

    private static final Method EQUALS_METHOD;
    private static final Method HASHCODE_METHOD;

    static {
        Method equalsMethod;
        Method hashCodeMethod;
        try {
            equalsMethod = Object.class.getMethod("equals", Object.class);
            hashCodeMethod = Object.class.getMethod("hashCode");
        } catch (NoSuchMethodException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
        EQUALS_METHOD = equalsMethod;
        HASHCODE_METHOD = hashCodeMethod;
    }

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
     * @param mapper An action that is invoked for each source object in the graph that is to be adapted. The action can influence how the source object is adapted via the provided {@link
     * SourceObjectMapping}.
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
        if (targetType.isEnum()) {
            return adaptToEnum(targetType, sourceObject);
        }

        MixInMethodInvoker mixInMethodInvoker = null;
        if (mapping.mixInType != null) {
            mixInMethodInvoker = new MixInMethodInvoker(mapping.mixInType, new AdaptingMethodInvoker(mapper, new ReflectionMethodInvoker()));
        }
        MethodInvoker overrideInvoker = chainInvokers(mixInMethodInvoker, mapping.overrideInvoker);
        Object proxy = Proxy.newProxyInstance(wrapperType.getClassLoader(), new Class<?>[]{wrapperType}, new InvocationHandlerImpl(sourceObject, overrideInvoker, mapper));
        if (mixInMethodInvoker != null) {
            mixInMethodInvoker.setProxy(proxy);
        }
        return wrapperType.cast(proxy);
    }

    private MethodInvoker chainInvokers(MixInMethodInvoker mixInMethodInvoker, MethodInvoker overrideInvoker) {
        if (mixInMethodInvoker == null) {
            return overrideInvoker;
        }
        if (overrideInvoker == NO_OP_HANDLER) {
            return mixInMethodInvoker;
        }
        return new ChainedMethodInvoker(mixInMethodInvoker, overrideInvoker);
    }

    private static <T, S> T adaptToEnum(Class<T> targetType, S sourceObject) {
        try {
            String literal;
            if (sourceObject instanceof Enum) {
                literal = ((Enum<?>) sourceObject).name();
            } else if (sourceObject instanceof String) {
                literal = (String) sourceObject;
            } else {
                literal = sourceObject.toString();
            }
            NotationParser<String, T> parser = new NotationConverterToNotationParserAdapter<String, T>(new EnumFromCharSequenceNotationParser(targetType));
            T parsedLiteral = parser.parseNotation(literal);
            return targetType.cast(parsedLiteral);
        } catch (TypeConversionException e) {
            throw new IllegalArgumentException(String.format("Can't convert '%s' to enum type '%s'", sourceObject, targetType.getSimpleName()), e);
        }
    }

    private Object convert(Type targetType, Object sourceObject, Action<? super SourceObjectMapping> mapping) {
        if (targetType instanceof ParameterizedType) {
            ParameterizedType parameterizedTargetType = (ParameterizedType) targetType;
            if (parameterizedTargetType.getRawType() instanceof Class) {
                Class<?> rawClass = (Class<?>) parameterizedTargetType.getRawType();
                if (Iterable.class.isAssignableFrom(rawClass)) {
                    Type targetElementType = getElementType(parameterizedTargetType, 0);
                    return convertCollectionInternal(rawClass, targetElementType, (Iterable<?>) sourceObject, mapping);
                }
                if (Map.class.isAssignableFrom(rawClass)) {
                    Type targetKeyType = getElementType(parameterizedTargetType, 0);
                    Type targetValueType = getElementType(parameterizedTargetType, 1);
                    return convertMap(rawClass, targetKeyType, targetValueType, (Map<?, ?>) sourceObject, mapping);
                }
            }
        }
        if (targetType instanceof Class) {
            if (((Class) targetType).isPrimitive()) {
                return sourceObject;
            }
            return adapt((Class) targetType, sourceObject, mapping);
        }
        throw new UnsupportedOperationException(String.format("Cannot convert object of %s to %s.", sourceObject.getClass(), targetType));
    }

    private Map<Object, Object> convertMap(Class<?> mapClass, Type targetKeyType, Type targetValueType, Map<?, ?> sourceObject, Action<? super SourceObjectMapping> mapping) {
        Map<Object, Object> convertedElements = collectionMapper.createEmptyMap(mapClass);
        for (Map.Entry<?, ?> entry : sourceObject.entrySet()) {
            convertedElements.put(convert(targetKeyType, entry.getKey(), mapping), convert(targetValueType, entry.getValue(), mapping));
        }
        return convertedElements;
    }

    private Object convertCollectionInternal(Class<?> collectionClass, Type targetElementType, Iterable<?> sourceObject, Action<? super SourceObjectMapping> mapping) {
        Collection<Object> convertedElements = collectionMapper.createEmptyCollection(collectionClass);
        convertCollectionInternal(convertedElements, targetElementType, sourceObject, mapping);
        if (collectionClass.equals(DomainObjectSet.class)) {
            return new ImmutableDomainObjectSet(convertedElements);
        } else {
            return convertedElements;
        }
    }

    private void convertCollectionInternal(Collection<Object> targetCollection, Type targetElementType, Iterable<?> sourceObject, Action<? super SourceObjectMapping> mapping) {
        for (Object element : sourceObject) {
            targetCollection.add(convert(targetElementType, element, mapping));
        }
    }

    private Type getElementType(ParameterizedType type, int index) {
        Type elementType = type.getActualTypeArguments()[index];
        if (elementType instanceof WildcardType) {
            WildcardType wildcardType = (WildcardType) elementType;
            return wildcardType.getUpperBounds()[0];
        }
        return elementType;
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
            if (EQUALS_METHOD.equals(method)) {
                Object param = params[0];
                if (param == null || !Proxy.isProxyClass(param.getClass())) {
                    return false;
                }
                InvocationHandler other = Proxy.getInvocationHandler(param);
                return equals(other);
            } else if (HASHCODE_METHOD.equals(method)) {
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
                invocation.setResult(convert(invocation.getGenericReturnType(), invocation.getResult(), mapping));
            }
        }
    }

    private static class MethodInvocationCache {
        private final Map<MethodInvocationKey, Optional<Method>> store = new HashMap<MethodInvocationKey, Optional<Method>>();
        private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        private final static long MINIMAL_CLEANUP_INTERVAL = 30000;

        // For stats we don't really care about thread safety
        private int cacheMiss;
        private int cacheHit;
        private int evict;

        private long lastCleanup = System.currentTimeMillis();

        private static class MethodInvocationKey {
            private final SoftReference<Class<?>> lookupClass;
            private final String methodName;
            private final SoftReference<Class<?>[]> parameterTypes;
            private final int hashCode;

            private MethodInvocationKey(Class<?> lookupClass, String methodName, Class<?>[] parameterTypes) {
                this.lookupClass = new SoftReference<Class<?>>(lookupClass);
                this.methodName = methodName;
                this.parameterTypes = new SoftReference<Class<?>[]>(parameterTypes);
                // hashcode will always be used, so we precompute it in order to make sure we
                // won't compute it multiple times during comparisons
                int result = lookupClass != null ? lookupClass.hashCode() : 0;
                result = 31 * result + (methodName != null ? methodName.hashCode() : 0);
                result = 31 * result + Arrays.hashCode(parameterTypes);
                this.hashCode = result;
            }

            public boolean isDirty() {
                return lookupClass.get() == null || parameterTypes.get() == null;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) {
                    return true;
                }
                if (o == null || getClass() != o.getClass()) {
                    return false;
                }

                MethodInvocationKey that = (MethodInvocationKey) o;

                if (isDirty() && that.isDirty()) {
                    return true;
                }
                if (!eq(lookupClass, that.lookupClass)) {
                    return false;
                }
                if (!methodName.equals(that.methodName)) {
                    return false;
                }
                return eq(parameterTypes, that.parameterTypes);

            }

            private static boolean eq(SoftReference<?> aRef, SoftReference<?> bRef) {
                Object a = aRef.get();
                Object b = bRef.get();
                return eq(a, b);
            }

            private static boolean eq(Object a, Object b) {
                if (a == b) {
                    return true;
                }
                if (a == null) {
                    return false;
                }
                if (a.getClass().isArray()) {
                    return Arrays.equals((Object[]) a, (Object[]) b);
                }
                return a.equals(b);
            }

            @Override
            public int hashCode() {
                return hashCode;
            }
        }

        public Method get(MethodInvocation invocation) {
            Class<?> owner = invocation.getDelegate().getClass();
            String name = invocation.getName();
            Class<?>[] parameterTypes = invocation.getParameterTypes();
            MethodInvocationKey key = new MethodInvocationKey(
                owner,
                name,
                parameterTypes
            );
            lock.readLock().lock();
            Optional<Method> cached = store.get(key);
            if (cached == null) {
                cacheMiss++;
                lock.readLock().unlock();
                lock.writeLock().lock();
                try {
                    cached = store.get(key);
                    if (cached == null) {
                        cached = lookup(owner, name, parameterTypes);
                        if (cacheMiss % 10 == 0) {
                            removeDirtyEntries();
                        }
                        store.put(key, cached);
                    }
                    lock.readLock().lock();
                } finally {
                    lock.writeLock().unlock();
                }
            } else {
                cacheHit++;
            }
            try {
                return cached.orNull();
            } finally {
                lock.readLock().unlock();
            }
        }

        /**
         * Removes dirty entries from the cache. Calling System.currentTimeMillis() is costly so we should try to limit calls to this method. This method will only trigger cleanup at most once per
         * 30s.
         */
        private void removeDirtyEntries() {
            long now = System.currentTimeMillis();
            if (now - lastCleanup < MINIMAL_CLEANUP_INTERVAL) {
                return;
            }
            lock.writeLock().lock();
            try {
                for (MethodInvocationKey key : new LinkedList<MethodInvocationKey>(store.keySet())) {
                    if (key.isDirty()) {
                        evict++;
                        store.remove(key);
                    }
                }
            } finally {
                lastCleanup = now;
                lock.writeLock().unlock();
            }
        }

        private static Optional<Method> lookup(Class<?> sourceClass, String methodName, Class<?>[] parameterTypes) {
            Method match;
            try {
                match = sourceClass.getMethod(methodName, parameterTypes);
            } catch (NoSuchMethodException e) {
                return Optional.absent();
            }

            LinkedList<Class<?>> queue = new LinkedList<Class<?>>();
            queue.add(sourceClass);
            while (!queue.isEmpty()) {
                Class<?> c = queue.removeFirst();
                try {
                    match = c.getMethod(methodName, parameterTypes);
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
            return Optional.of(match);
        }

        @Override
        public String toString() {
            return "Cache size: " + store.size() + " Hits: " + cacheHit + " Miss: " + cacheMiss + " Evicted: " + evict;
        }
    }

    private static class ReflectionMethodInvoker implements MethodInvoker {
        private final static MethodInvocationCache LOOKUP_CACHE = new MethodInvocationCache();

        public void invoke(MethodInvocation invocation) throws Throwable {
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

        private static Method locateMethod(MethodInvocation invocation) {
            return LOOKUP_CACHE.get(invocation);
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
            if (method.isGetter()) {
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
        private static final Class[] EMPTY_CLASS_ARRAY = new Class[0];
        private final MethodInvoker next;

        private SafeMethodInvoker(MethodInvoker next) {
            this.next = next;
        }

        public void invoke(MethodInvocation invocation) throws Throwable {
            next.invoke(invocation);
            if (invocation.found() || invocation.getParameterTypes().length != 1) {
                return;
            }

            if (!invocation.isIsOrGet()) {
                return;
            }

            MethodInvocation getterInvocation = new MethodInvocation(invocation.getName(), invocation.getReturnType(), invocation.getGenericReturnType(), EMPTY_CLASS_ARRAY, invocation.getDelegate(), EMPTY);
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

            String getterName = "get" + matcher.group(1);
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
                instance = DirectInstantiator.INSTANCE.newInstance(mixInClass, proxy);
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
