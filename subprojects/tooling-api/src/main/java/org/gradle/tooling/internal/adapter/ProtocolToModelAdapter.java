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
import org.gradle.api.Nullable;
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
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Adapts some source object to some target view type.
 */
public class ProtocolToModelAdapter implements ObjectGraphAdapter {
    private static final MethodInvoker NO_OP_HANDLER = new NoOpMethodInvoker();
    private static final Action<SourceObjectMapping> NO_OP_MAPPER = new NoOpMapping();
    private static final TargetTypeProvider IDENTITY_TYPE_PROVIDER = new TargetTypeProvider() {
        public <T> Class<? extends T> getTargetType(Class<T> initialTargetType, Object protocolObject) {
            return initialTargetType;
        }
    };
    private static final ReflectionMethodInvoker REFLECTION_METHOD_INVOKER = new ReflectionMethodInvoker();
    private static final CollectionMapper COLLECTION_MAPPER = new CollectionMapper();
    private static final Object[] EMPTY = new Object[0];
    private static final Pattern IS_SUPPORT_METHOD = Pattern.compile("is(\\w+)Supported");
    private static final Method EQUALS_METHOD;
    private static final Method HASHCODE_METHOD;

    private final TargetTypeProvider targetTypeProvider;

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
     * Creates an adapter for a single object graph. Each object adapted by the returned adapter is treated as part of the same object graph, for the purposes of caching etc.
     */
    public ObjectGraphAdapter newGraph() {
        final AdaptedGraphDetails graphDetails = new AdaptedGraphDetails(NO_OP_MAPPER, targetTypeProvider);
        return new ObjectGraphAdapter() {
            @Override
            public <T> T adapt(Class<T> targetType, Object sourceObject) {
                return createView(targetType, sourceObject, graphDetails);
            }
        };
    }

    /**
     * Adapts the source object to a view object.
     */
    @Override
    public <T> T adapt(Class<T> targetType, Object sourceObject) {
        if (sourceObject == null) {
            return null;
        }
        return createView(targetType, sourceObject, new AdaptedGraphDetails(NO_OP_MAPPER, targetTypeProvider));
    }

    /**
     * Creates a builder for views of the given type.
     */
    public <T> ViewBuilder<T> builder(final Class<T> viewType) {
        return new ViewBuilder<T>() {
            List<Class<?>> mixInClasses = new ArrayList<Class<?>>();
            List<Action<SourceObjectMapping>> mappers = new ArrayList<Action<SourceObjectMapping>>();

            @Override
            public ViewBuilder<T> mixIn(Class<?> mixInType) {
                mixInClasses.add(mixInType);
                return this;
            }

            @Override
            public ViewBuilder<T> mixInTo(final Class<?> targetType, final Object mixIn) {
                mappers.add(new MixInBeanMappingAction(targetType, mixIn));
                return this;
            }

            @Override
            public ViewBuilder<T> mixInTo(final Class<?> targetType, final Class<?> mixInType) {
                mappers.add(new MixInTypeMappingAction(targetType, mixInType));
                return this;
            }

            @Override
            public T build(@Nullable final Object sourceObject) {
                if (sourceObject == null) {
                    return null;
                }

                Action<SourceObjectMapping> mapper;
                if (mappers.isEmpty() && mixInClasses.isEmpty()) {
                    mapper = NO_OP_MAPPER;
                } else {
                    mapper = new MixInMappingAction(sourceObject, mixInClasses, mappers);
                }
                return createView(viewType, sourceObject, new AdaptedGraphDetails(mapper, targetTypeProvider));
            }
        };
    }

    private static <T> T createView(Class<T> targetType, Object sourceObject, AdaptedGraphDetails graphDetails) {
        if (sourceObject == null) {
            return null;
        }

        // Calculate the actual type
        Class<? extends T> viewType = graphDetails.typeProvider.getTargetType(targetType, sourceObject);

        if (viewType.isInstance(sourceObject)) {
            return viewType.cast(sourceObject);
        }
        if (targetType.isEnum()) {
            return adaptToEnum(targetType, sourceObject);
        }

        ViewKey viewKey = new ViewKey(viewType, sourceObject);
        Object view = graphDetails.adaptedObjects.get(viewKey);
        if (view != null) {
            return targetType.cast(view);
        }

        // Create a proxy
        InvocationHandlerImpl handler = new InvocationHandlerImpl(targetType, sourceObject, graphDetails);
        Object proxy = Proxy.newProxyInstance(viewType.getClassLoader(), new Class<?>[]{viewType}, handler);
        handler.attachProxy(proxy);

        return viewType.cast(proxy);
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

    private static Object convert(Type targetType, Object sourceObject, AdaptedGraphDetails graphDetails) {
        if (targetType instanceof ParameterizedType) {
            ParameterizedType parameterizedTargetType = (ParameterizedType) targetType;
            if (parameterizedTargetType.getRawType() instanceof Class) {
                Class<?> rawClass = (Class<?>) parameterizedTargetType.getRawType();
                if (Iterable.class.isAssignableFrom(rawClass)) {
                    Type targetElementType = getElementType(parameterizedTargetType, 0);
                    return convertCollectionInternal(rawClass, targetElementType, (Iterable<?>) sourceObject, graphDetails);
                }
                if (Map.class.isAssignableFrom(rawClass)) {
                    Type targetKeyType = getElementType(parameterizedTargetType, 0);
                    Type targetValueType = getElementType(parameterizedTargetType, 1);
                    return convertMap(rawClass, targetKeyType, targetValueType, (Map<?, ?>) sourceObject, graphDetails);
                }
            }
        }
        if (targetType instanceof Class) {
            if (((Class) targetType).isPrimitive()) {
                return sourceObject;
            }
            return createView((Class) targetType, sourceObject, graphDetails);
        }
        throw new UnsupportedOperationException(String.format("Cannot convert object of %s to %s.", sourceObject.getClass(), targetType));
    }

    private static Map<Object, Object> convertMap(Class<?> mapClass, Type targetKeyType, Type targetValueType, Map<?, ?> sourceObject, AdaptedGraphDetails graphDetails) {
        Map<Object, Object> convertedElements = COLLECTION_MAPPER.createEmptyMap(mapClass);
        for (Map.Entry<?, ?> entry : sourceObject.entrySet()) {
            convertedElements.put(convert(targetKeyType, entry.getKey(), graphDetails), convert(targetValueType, entry.getValue(), graphDetails));
        }
        return convertedElements;
    }

    private static Object convertCollectionInternal(Class<?> collectionClass, Type targetElementType, Iterable<?> sourceObject, AdaptedGraphDetails graphDetails) {
        Collection<Object> convertedElements = COLLECTION_MAPPER.createEmptyCollection(collectionClass);
        convertCollectionInternal(convertedElements, targetElementType, sourceObject, graphDetails);
        if (collectionClass.equals(DomainObjectSet.class)) {
            return new ImmutableDomainObjectSet(convertedElements);
        } else {
            return convertedElements;
        }
    }

    private static void convertCollectionInternal(Collection<Object> targetCollection, Type targetElementType, Iterable<?> sourceObject, AdaptedGraphDetails graphDetails) {
        for (Object element : sourceObject) {
            targetCollection.add(convert(targetElementType, element, graphDetails));
        }
    }

    private static Type getElementType(ParameterizedType type, int index) {
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
        return handler.sourceObject;
    }

    private static class AdaptedGraphDetails implements Serializable {
        // Transient, don't serialize all the views that happen to have been visited, recreate them when visited via the deserialized view
        private transient Map<ViewKey, Object> adaptedObjects = new HashMap<ViewKey, Object>();
        private final Action<? super SourceObjectMapping> mapper;
        private final TargetTypeProvider typeProvider;

        AdaptedGraphDetails(Action<? super SourceObjectMapping> mapper, TargetTypeProvider typeProvider) {
            this.mapper = mapper;
            this.typeProvider = typeProvider;
        }

        private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
            in.defaultReadObject();
            adaptedObjects = new HashMap<ViewKey, Object>();
        }
    }

    private static class ViewKey implements Serializable {
        private final Class<?> type;
        private final Object source;

        ViewKey(Class<?> type, Object source) {
            this.type = type;
            this.source = source;
        }

        @Override
        public boolean equals(Object obj) {
            ViewKey other = (ViewKey) obj;
            return other.type.equals(type) && other.source == source;
        }

        @Override
        public int hashCode() {
            return type.hashCode() ^ source.hashCode();
        }
    }

    private static class DefaultSourceObjectMapping implements SourceObjectMapping {
        private final Object protocolObject;
        private final Class<?> targetType;
        private Class<?> mixInType;
        private Object mixInBean;

        DefaultSourceObjectMapping(Object protocolObject, Class<?> targetType) {
            this.protocolObject = protocolObject;
            this.targetType = targetType;
        }

        public Object getSourceObject() {
            return protocolObject;
        }

        public Class<?> getTargetType() {
            return targetType;
        }

        public void mixIn(Class<?> mixInBeanType) {
            if (mixInType != null) {
                throw new UnsupportedOperationException("Mixing in multiple beans is currently not supported.");
            }
            mixInType = mixInBeanType;
        }

        @Override
        public void mixIn(Object mixInBean) {
            if (this.mixInBean != null) {
                throw new UnsupportedOperationException("Mixing in multiple invokers is currently not supported.");
            }
            this.mixInBean = mixInBean;
        }
    }

    private static class NoOpMapping implements Action<SourceObjectMapping>, Serializable {
        public void execute(SourceObjectMapping mapping) {
        }
    }

    private static class InvocationHandlerImpl implements InvocationHandler, Serializable {
        private final Class<?> targetType;
        private final Object sourceObject;
        private final AdaptedGraphDetails graphDetails;
        private Object proxy;
        // Recreate the invoker when deserialized, rather than serialize all its state
        private transient MethodInvoker invoker;

        InvocationHandlerImpl(Class<?> targetType, Object sourceObject, AdaptedGraphDetails graphDetails) {
            this.targetType = targetType;
            this.sourceObject = sourceObject;
            this.graphDetails = graphDetails;
            setup();
        }

        private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
            in.defaultReadObject();
            setup();
            graphDetails.adaptedObjects.put(new ViewKey(targetType, sourceObject), proxy);
        }

        private void setup() {
            DefaultSourceObjectMapping mapping = new DefaultSourceObjectMapping(sourceObject, targetType);
            graphDetails.mapper.execute(mapping);

            MethodInvoker mixInMethodInvoker = NO_OP_HANDLER;
            if (mapping.mixInType != null) {
                mixInMethodInvoker = new ClassMixInMethodInvoker(mapping.mixInType, new AdaptingMethodInvoker(graphDetails, REFLECTION_METHOD_INVOKER));
            } else if (mapping.mixInBean != null) {
                mixInMethodInvoker = new BeanMixInMethodInvoker(mapping.mixInBean, new AdaptingMethodInvoker(graphDetails, REFLECTION_METHOD_INVOKER));
            }

            invoker = new SupportedPropertyInvoker(
                new SafeMethodInvoker(
                    new PropertyCachingMethodInvoker(
                        new AdaptingMethodInvoker(graphDetails,
                            new ChainedMethodInvoker(
                                mixInMethodInvoker,
                                REFLECTION_METHOD_INVOKER)))));
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
            return sourceObject.equals(other.sourceObject);
        }

        @Override
        public int hashCode() {
            return sourceObject.hashCode();
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

            MethodInvocation invocation = new MethodInvocation(method.getName(), method.getReturnType(), method.getGenericReturnType(), method.getParameterTypes(), target, sourceObject, params);
            invoker.invoke(invocation);
            if (!invocation.found()) {
                String methodName = method.getDeclaringClass().getSimpleName() + "." + method.getName() + "()";
                throw Exceptions.unsupportedMethod(methodName);
            }
            return invocation.getResult();
        }

        void attachProxy(Object proxy) {
            this.proxy = proxy;
            graphDetails.adaptedObjects.put(new ViewKey(targetType, sourceObject), proxy);
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

    private static class AdaptingMethodInvoker implements MethodInvoker {
        private final AdaptedGraphDetails graphDetails;
        private final MethodInvoker next;

        private AdaptingMethodInvoker(AdaptedGraphDetails graphDetails, MethodInvoker next) {
            this.graphDetails = graphDetails;
            this.next = next;
        }

        public void invoke(MethodInvocation invocation) throws Throwable {
            next.invoke(invocation);
            if (invocation.found() && invocation.getResult() != null) {
                invocation.setResult(convert(invocation.getGenericReturnType(), invocation.getResult(), graphDetails));
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
        private final MethodInvocationCache lookupCache = new MethodInvocationCache();

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

        private Method locateMethod(MethodInvocation invocation) {
            return lookupCache.get(invocation);
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

            MethodInvocation getterInvocation = new MethodInvocation(invocation.getName(), invocation.getReturnType(), invocation.getGenericReturnType(), EMPTY_CLASS_ARRAY, invocation.getTarget(), invocation.getDelegate(), EMPTY);
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
            MethodInvocation getterInvocation = new MethodInvocation(getterName, invocation.getReturnType(), invocation.getGenericReturnType(), new Class[0], invocation.getTarget(), invocation.getDelegate(), EMPTY);
            next.invoke(getterInvocation);
            invocation.setResult(getterInvocation.found());
        }
    }

    private static class BeanMixInMethodInvoker implements MethodInvoker {
        private final Object instance;
        private final MethodInvoker next;

        public BeanMixInMethodInvoker(Object instance, MethodInvoker next) {
            this.instance = instance;
            this.next = next;
        }

        @Override
        public void invoke(MethodInvocation invocation) throws Throwable {
            MethodInvocation beanInvocation = new MethodInvocation(invocation.getName(), invocation.getReturnType(), invocation.getGenericReturnType(), invocation.getParameterTypes(), instance, instance, invocation.getParameters());
            next.invoke(beanInvocation);
            if (beanInvocation.found()) {
                invocation.setResult(beanInvocation.getResult());
            }
        }
    }

    private static class ClassMixInMethodInvoker implements MethodInvoker {
        private Object instance;
        private final Class<?> mixInClass;
        private final MethodInvoker next;
        private final ThreadLocal<MethodInvocation> current = new ThreadLocal<MethodInvocation>();

        ClassMixInMethodInvoker(Class<?> mixInClass, MethodInvoker next) {
            this.mixInClass = mixInClass;
            this.next = next;
        }

        public void invoke(MethodInvocation invocation) throws Throwable {
            if (current.get() != null) {
                // Already invoking a method on the mix-in
                return;
            }

            if (instance == null) {
                instance = DirectInstantiator.INSTANCE.newInstance(mixInClass, invocation.getTarget());
            }
            MethodInvocation beanInvocation = new MethodInvocation(invocation.getName(), invocation.getReturnType(), invocation.getGenericReturnType(), invocation.getParameterTypes(), instance, instance, invocation.getParameters());
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
    }

    private static class MixInMappingAction implements Action<SourceObjectMapping>, Serializable {
        private final Object sourceObject;
        private final List<Class<?>> mixInClasses;
        private final List<Action<SourceObjectMapping>> mappers;

        MixInMappingAction(Object sourceObject, List<Class<?>> mixInClasses, List<Action<SourceObjectMapping>> mappers) {
            this.sourceObject = sourceObject;
            this.mixInClasses = mixInClasses;
            this.mappers = mappers;
        }

        @Override
        public void execute(SourceObjectMapping sourceObjectMapping) {
            if (sourceObjectMapping.getSourceObject() == sourceObject) {
                for (Class<?> mixInClass : mixInClasses) {
                    sourceObjectMapping.mixIn(mixInClass);
                }
            }
            for (Action<SourceObjectMapping> action : mappers) {
                action.execute(sourceObjectMapping);
            }
        }
    }

    private static class MixInBeanMappingAction implements Action<SourceObjectMapping> , Serializable{
        private final Class<?> targetType;
        private final Object mixIn;

        MixInBeanMappingAction(Class<?> targetType, Object mixIn) {
            this.targetType = targetType;
            this.mixIn = mixIn;
        }

        @Override
        public void execute(SourceObjectMapping sourceObjectMapping) {
            if (targetType.isAssignableFrom(sourceObjectMapping.getTargetType())) {
                sourceObjectMapping.mixIn(mixIn);
            }
        }
    }

    private static class MixInTypeMappingAction implements Action<SourceObjectMapping>, Serializable {
        private final Class<?> targetType;
        private final Class<?> mixInType;

        MixInTypeMappingAction(Class<?> targetType, Class<?> mixInType) {
            this.targetType = targetType;
            this.mixInType = mixInType;
        }

        @Override
        public void execute(SourceObjectMapping sourceObjectMapping) {
            if (targetType.isAssignableFrom(sourceObjectMapping.getTargetType())) {
                sourceObjectMapping.mixIn(mixInType);
            }
        }
    }
}
