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
import org.gradle.internal.Cast;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.reflect.DirectInstantiator;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.internal.time.CountdownTimer;
import org.gradle.internal.time.Time;
import org.gradle.tooling.ToolingModelContract;
import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.internal.Exceptions;
import org.gradle.tooling.model.internal.ImmutableDomainObjectSet;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Adapts some source object to some target view type.
 */
@ServiceScope(Scope.Global.class)
public class ProtocolToModelAdapter implements ObjectGraphAdapter {
    private static final ViewDecoration NO_OP_MAPPER = new NoOpDecoration();
    private static final TargetTypeProvider IDENTITY_TYPE_PROVIDER = new TargetTypeProvider() {
        @Override
        public <T> Class<? extends T> getTargetType(Class<T> initialTargetType, Object protocolObject) {
            return initialTargetType;
        }
    };

    private static final Pattern UPPER_LOWER_PATTERN = Pattern.compile("(?m)([A-Z]*)([a-z0-9]*)");
    private static final ReflectionMethodInvoker REFLECTION_METHOD_INVOKER = new ReflectionMethodInvoker();
    private static final TypeInspector TYPE_INSPECTOR = new TypeInspector();
    private static final CollectionMapper COLLECTION_MAPPER = new CollectionMapper();
    private static final Object[] EMPTY = new Object[0];
    private static final Class[] EMPTY_CLASS_ARRAY = new Class[0];
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
        final ViewGraphDetails graphDetails = new ViewGraphDetails(targetTypeProvider);
        return new ObjectGraphAdapter() {
            @Override
            public <T> T adapt(Class<T> targetType, Object sourceObject) {
                return createView(targetType, sourceObject, NO_OP_MAPPER, graphDetails);
            }

            @Override
            public <T> ViewBuilder<T> builder(Class<T> viewType) {
                return new DefaultViewBuilder<T>(viewType, graphDetails);
            }
        };
    }

    /**
     * Adapts the source object to a view object.
     */
    @Override
    public <T> T adapt(Class<T> targetType, @Nullable Object sourceObject) {
        if (sourceObject == null) {
            return null;
        }
        return createView(targetType, sourceObject, NO_OP_MAPPER, new ViewGraphDetails(targetTypeProvider));
    }

    /**
     * Creates a builder for views of the given type.
     */
    @Override
    public <T> ViewBuilder<T> builder(final Class<T> viewType) {
        return new DefaultViewBuilder<T>(viewType);
    }

    @Nullable
    private static <T> T createView(Class<T> targetType, @Nullable Object sourceObject, ViewDecoration decoration, ViewGraphDetails graphDetails) {
        if (sourceObject == null) {
            return null;
        }
        if (sourceObject instanceof Supplier) {
            return createView(targetType, ((Supplier<?>) sourceObject).get(), decoration, graphDetails);
        }

        // Calculate the actual type
        Class<? extends T> viewType = graphDetails.typeProvider.getTargetType(targetType, sourceObject);

        if (viewType.isInstance(sourceObject)) {
            return viewType.cast(sourceObject);
        }
        if (targetType.isEnum()) {
            return adaptToEnum(targetType, sourceObject);
        }

        // Restrict the decorations to those required to decorate all views reachable from this type
        ViewDecoration decorationsForThisType = decoration.isNoOp() ? decoration : decoration.restrictTo(TYPE_INSPECTOR.getReachableTypes(targetType));

        ViewKey viewKey = new ViewKey(viewType, decorationsForThisType);
        Object view = graphDetails.getViewFor(sourceObject, viewKey);
        if (view != null) {
            return targetType.cast(view);
        }

        // Create a proxy
        InvocationHandlerImpl handler = new InvocationHandlerImpl(targetType, sourceObject, decorationsForThisType, graphDetails);
        Class<?>[] modelContractInterfaces = getModelContractInterfaces(targetType, sourceObject, viewType);
        Object proxy = Proxy.newProxyInstance(viewType.getClassLoader(), modelContractInterfaces, handler);
        handler.attachProxy(proxy);

        graphDetails.putViewFor(sourceObject, viewKey, proxy);

        return viewType.cast(proxy);
    }

    private static <T> Class<?>[] getModelContractInterfaces(Class<T> targetType, Object sourceObject, Class<? extends T> viewType) {
        Map<String, Class<?>> potentialSubInterfaces = getPotentialModelContractSubInterfaces(targetType);
        Set<Class<?>> actualSubInterfaces = getActualImplementedModelContractSubInterfaces(sourceObject, potentialSubInterfaces);

        List<Class<?>> modelContractInterfaces = new ArrayList<>();
        modelContractInterfaces.add(viewType); // base interface
        modelContractInterfaces.addAll(actualSubInterfaces);
        return modelContractInterfaces.toArray(new Class<?>[0]);
    }

    private static <T> Map<String, Class<?>> getPotentialModelContractSubInterfaces(Class<T> targetType) {
        HashMap<String, Class<?>> result = new HashMap<>();
        getPotentialModelContractSubInterfaces(targetType, new HashSet<Class<?>>(), result);
        return result;
    }

    private static <T> void getPotentialModelContractSubInterfaces(
        Class<T> targetType,
        Set<Class<?>> visited,
        Map<String, Class<?>> result
    ) {
        boolean isNew = visited.add(targetType);
        if (isNew) {
            Annotation[] annotations = targetType.getAnnotations();
            for (Annotation annotation : annotations) {
                if (annotation instanceof ToolingModelContract) {
                    Class<?>[] classes = ((ToolingModelContract) annotation).subTypes();
                    for (Class<?> clazz : classes) {
                        result.put(clazz.getName(), clazz);
                        getPotentialModelContractSubInterfaces(clazz, visited, result);
                    }
                }
            }
        }
    }

    private static Set<Class<?>> getActualImplementedModelContractSubInterfaces(Object sourceObject, Map<String, Class<?>> potentialModelContractInterfaces) {
        if (potentialModelContractInterfaces.isEmpty()) {
            return Collections.emptySet();
        }

        Set<Class<?>> allImplementedInterfaces = walkTypeHierarchyAndExtractInterfaces(sourceObject.getClass());

        // keep only those implemented interfaces which are in model contract set
        Set<Class<?>> filteredImplementedInterfaces = new HashSet<>();
        for (Class<?> i : allImplementedInterfaces) {
            Class<?> actualSubType = potentialModelContractInterfaces.get(i.getName());
            if (actualSubType != null) {
                filteredImplementedInterfaces.add(actualSubType);
            }
        }

        return filteredImplementedInterfaces;
    }

    private static <T> Set<Class<?>> walkTypeHierarchyAndExtractInterfaces(Class<?> clazz) {
        Set<Class<?>> seenInterfaces = new HashSet<>();
        Queue<Class<?>> queue = new ArrayDeque<>();
        queue.add(clazz);
        Class<?> type;
        while ((type = queue.poll()) != null) {
            Class<?> superclass = type.getSuperclass();
            if (superclass != null) {
                queue.add(superclass);
            }
            for (Class<?> iface : type.getInterfaces()) {
                if (seenInterfaces.add(iface)) {
                    queue.add(Cast.<Class<? super T>>uncheckedCast(iface));
                }
            }
        }
        return seenInterfaces;
    }

    private static <T, S> T adaptToEnum(Class<T> targetType, S sourceObject) {
        String literal;
        if (sourceObject instanceof Enum) {
            literal = ((Enum<?>) sourceObject).name();
        } else if (sourceObject instanceof String) {
            literal = (String) sourceObject;
        } else {
            literal = sourceObject.toString();
        }

        @SuppressWarnings("unchecked")
        T result = (T) toEnum((Class<Enum>) targetType, literal);
        return result;
    }

    // Copied from GUtils.toEnum(). We can't use that class here as it depends on Java 8 classe
    // which breaks the TAPI build actions when the target Gradle version is running on Java 6.
    public static <T extends Enum<T>> T toEnum(Class<? extends T> enumType, String literal) {
        T match = findEnumValue(enumType, literal);
        if (match != null) {
            return match;
        }

        final String alternativeLiteral = toWords(literal, '_');
        match = findEnumValue(enumType, alternativeLiteral);
        if (match != null) {
            return match;
        }

        String sep = "";
        StringBuilder builder = new StringBuilder();
        for (T ec : enumType.getEnumConstants()) {
            builder.append(sep);
            builder.append(ec.name());
            sep = ", ";
        }

        throw new IllegalArgumentException(
            String.format("Cannot convert string value '%s' to an enum value of type '%s' (valid case insensitive values: %s)",
                literal, enumType.getName(), builder.toString())
        );
    }

    @Nullable
    private static <T extends Enum<T>> T findEnumValue(Class<? extends T> enumType, final String literal) {
        for (T ec : enumType.getEnumConstants()) {
            if (ec.name().equalsIgnoreCase(literal)) {
                return ec;
            }
        }
        return null;
    }

    @Nullable
    public static String toWords(@Nullable CharSequence string, char separator) {
        if (string == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        int pos = 0;
        Matcher matcher = UPPER_LOWER_PATTERN.matcher(string);
        while (pos < string.length()) {
            matcher.find(pos);
            if (matcher.end() == pos) {
                // Not looking at a match
                pos++;
                continue;
            }
            if (builder.length() > 0) {
                builder.append(separator);
            }
            String group1 = matcher.group(1).toLowerCase(Locale.ROOT);
            String group2 = matcher.group(2);
            if (group2.length() == 0) {
                builder.append(group1);
            } else {
                if (group1.length() > 1) {
                    builder.append(group1.substring(0, group1.length() - 1));
                    builder.append(separator);
                    builder.append(group1.substring(group1.length() - 1));
                } else {
                    builder.append(group1);
                }
                builder.append(group2);
            }
            pos = matcher.end();
        }

        return builder.toString();
    }

    @Nullable
    private static Object convert(Type targetType, Object sourceObject, ViewDecoration decoration, ViewGraphDetails graphDetails) {
        if (targetType instanceof ParameterizedType) {
            ParameterizedType parameterizedTargetType = (ParameterizedType) targetType;
            if (parameterizedTargetType.getRawType() instanceof Class) {
                Class<?> rawClass = (Class<?>) parameterizedTargetType.getRawType();
                if (Iterable.class.isAssignableFrom(rawClass)) {
                    Type targetElementType = getElementType(parameterizedTargetType, 0);
                    return convertCollectionInternal(rawClass, targetElementType, (Iterable<?>) sourceObject, decoration, graphDetails);
                }
                if (Map.class.isAssignableFrom(rawClass)) {
                    Type targetKeyType = getElementType(parameterizedTargetType, 0);
                    Type targetValueType = getElementType(parameterizedTargetType, 1);
                    return convertMap(rawClass, targetKeyType, targetValueType, (Map<?, ?>) sourceObject, decoration, graphDetails);
                }
            }
        }
        if (targetType instanceof Class) {
            Class<Object> targetClassType = Cast.uncheckedNonnullCast(targetType);
            if (targetClassType.isPrimitive()) {
                return sourceObject;
            }
            return createView(targetClassType, sourceObject, decoration, graphDetails);
        }
        throw new UnsupportedOperationException(String.format("Cannot convert object of %s to %s.", sourceObject.getClass(), targetType));
    }

    private static Map<Object, Object> convertMap(Class<?> mapClass, Type targetKeyType, Type targetValueType, Map<?, ?> sourceObject, ViewDecoration decoration, ViewGraphDetails graphDetails) {
        Map<Object, Object> convertedElements = COLLECTION_MAPPER.createEmptyMap(mapClass);
        for (Map.Entry<?, ?> entry : sourceObject.entrySet()) {
            convertedElements.put(convert(targetKeyType, entry.getKey(), decoration, graphDetails), convert(targetValueType, entry.getValue(), decoration, graphDetails));
        }
        return convertedElements;
    }

    private static Object convertCollectionInternal(Class<?> collectionClass, Type targetElementType, Iterable<?> sourceObject, ViewDecoration decoration, ViewGraphDetails graphDetails) {
        Collection<Object> convertedElements = COLLECTION_MAPPER.createEmptyCollection(collectionClass);
        convertCollectionInternal(convertedElements, targetElementType, sourceObject, decoration, graphDetails);
        if (collectionClass.equals(DomainObjectSet.class)) {
            return new ImmutableDomainObjectSet<Object>(convertedElements);
        } else {
            return convertedElements;
        }
    }

    private static void convertCollectionInternal(Collection<Object> targetCollection, Type targetElementType, Iterable<?> sourceObject, ViewDecoration viewDecoration, ViewGraphDetails graphDetails) {
        for (Object element : sourceObject) {
            targetCollection.add(convert(targetElementType, element, viewDecoration, graphDetails));
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

    private static class ViewGraphDetails implements Serializable {
        // Transient, don't serialize all the views that happen to have been visited, recreate them when visited via the deserialized view
        private transient WeakIdentityHashMap<Object, Map<ViewKey, WeakReference<Object>>> views = new WeakIdentityHashMap<>();
        private final TargetTypeProvider typeProvider;

        ViewGraphDetails(TargetTypeProvider typeProvider) {
            this.typeProvider = typeProvider;
        }

        private void putViewFor(Object sourceObject, ViewKey key, Object proxy) {
            Map<ViewKey, WeakReference<Object>> viewsForSource = views.computeIfAbsent(sourceObject,
                new WeakIdentityHashMap.AbsentValueProvider<Map<ViewKey, WeakReference<Object>>>() {
                    @Override
                    public Map<ViewKey, WeakReference<Object>> provide() {
                        return new HashMap<>();
                    }
                });

            viewsForSource.put(key, new WeakReference<>(proxy));
        }

        @Nullable
        private Object getViewFor(Object sourceObject, ViewKey key) {
            Map<ViewKey, WeakReference<Object>> viewsForSource = views.get(sourceObject);

            if (viewsForSource == null) {
                return null;
            }

            WeakReference<Object> viewWeakRef = viewsForSource.get(key);
            if (viewWeakRef == null) {
                return null;
            }

            return viewWeakRef.get();
        }

        private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
            in.defaultReadObject();
            views = new WeakIdentityHashMap<>();
        }
    }

    private static class ViewKey implements Serializable {
        private final Class<?> type;
        private final ViewDecoration viewDecoration;

        ViewKey(Class<?> type, ViewDecoration viewDecoration) {
            this.type = type;
            this.viewDecoration = viewDecoration;
        }

        @Override
        public boolean equals(Object obj) {
            ViewKey other = (ViewKey) obj;
            return other.type.equals(type) && other.viewDecoration.equals(viewDecoration);
        }

        @Override
        public int hashCode() {
            return type.hashCode() ^ viewDecoration.hashCode();
        }
    }

    private static class InvocationHandlerImpl implements InvocationHandler, Serializable {
        private final Class<?> targetType;
        private final Object sourceObject;
        private final ViewDecoration decoration;
        private final ViewGraphDetails graphDetails;
        private Object proxy;
        // Recreate the invoker when deserialized, rather than serialize all its state
        private transient MethodInvoker invoker;

        InvocationHandlerImpl(Class<?> targetType, Object sourceObject, ViewDecoration decoration, ViewGraphDetails graphDetails) {
            this.targetType = targetType;
            this.sourceObject = sourceObject;
            this.decoration = decoration;
            this.graphDetails = graphDetails;
            setup();
        }

        private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
            in.defaultReadObject();
            setup();
            graphDetails.putViewFor(sourceObject, new ViewKey(targetType, decoration), proxy);
        }

        private void setup() {
            List<MethodInvoker> invokers = new ArrayList<MethodInvoker>();
            invokers.add(REFLECTION_METHOD_INVOKER);
            decoration.collectInvokers(sourceObject, targetType, invokers);

            MethodInvoker mixInMethodInvoker = invokers.size() == 1 ? invokers.get(0) : new ChainedMethodInvoker(invokers);

            invoker = new SupportedPropertyInvoker(
                new SafeMethodInvoker(
                    new PropertyCachingMethodInvoker(
                        new AdaptingMethodInvoker(decoration, graphDetails,
                            mixInMethodInvoker))));
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

        @Override
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

            MethodInvocation invocation = new MethodInvocation(method.getName(), method.getReturnType(), method.getGenericReturnType(), method.getParameterTypes(), target, targetType, sourceObject, params);
            invoker.invoke(invocation);
            if (!invocation.found()) {
                String methodName = method.getDeclaringClass().getSimpleName() + "." + method.getName() + "()";
                throw Exceptions.unsupportedMethod(methodName);
            }
            return invocation.getResult();
        }

        void attachProxy(Object proxy) {
            this.proxy = proxy;
        }
    }

    private static class ChainedMethodInvoker implements MethodInvoker {
        private final MethodInvoker[] invokers;

        private ChainedMethodInvoker(List<MethodInvoker> invokers) {
            this.invokers = invokers.toArray(new MethodInvoker[0]);
        }

        @Override
        public void invoke(MethodInvocation method) throws Throwable {
            for (int i = 0; !method.found() && i < invokers.length; i++) {
                MethodInvoker invoker = invokers[i];
                invoker.invoke(method);
            }
        }
    }

    private static class AdaptingMethodInvoker implements MethodInvoker {
        private final ViewDecoration decoration;
        private final ViewGraphDetails graphDetails;
        private final MethodInvoker next;

        private AdaptingMethodInvoker(ViewDecoration decoration, ViewGraphDetails graphDetails, MethodInvoker next) {
            this.decoration = decoration;
            this.graphDetails = graphDetails;
            this.next = next;
        }

        @Override
        public void invoke(MethodInvocation invocation) throws Throwable {
            next.invoke(invocation);
            if (invocation.found() && invocation.getResult() != null) {
                invocation.setResult(convert(invocation.getGenericReturnType(), invocation.getResult(), decoration, graphDetails));
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

        private CountdownTimer cleanupTimer = Time.startCountdownTimer(MINIMAL_CLEANUP_INTERVAL);

        private static class MethodInvocationKey {
            private final SoftReference<Class<?>> lookupClass;
            private final String methodName;
            private final SoftReference<Class<?>[]> parameterTypes;
            private final int hashCode;

            private MethodInvocationKey(@Nullable Class<?> lookupClass, @Nullable String methodName, Class<?>[] parameterTypes) {
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

        @Nullable
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
            if (!cleanupTimer.hasExpired()) {
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
                cleanupTimer.reset();
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

            LinkedList<Class<?>> queue = new LinkedList<>();
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

        @Override
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

        @Nullable
        private Method locateMethod(MethodInvocation invocation) {
            return lookupCache.get(invocation);
        }
    }

    private static class PropertyCachingMethodInvoker implements MethodInvoker {
        private Map<String, Object> properties = Collections.emptyMap();
        private Set<String> unknown = Collections.emptySet();
        private final MethodInvoker next;

        private PropertyCachingMethodInvoker(MethodInvoker next) {
            this.next = next;
        }

        @Override
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
                    markUnknown(method.getName());
                    return;
                }
                value = method.getResult();
                cachePropertyValue(method.getName(), value);
                return;
            }

            next.invoke(method);
        }

        private void markUnknown(String methodName) {
            if (unknown.isEmpty()) {
                unknown = new HashSet<String>();
            }
            unknown.add(methodName);
        }

        private void cachePropertyValue(String methodName, Object value) {
            if (properties.isEmpty()) {
                properties = new HashMap<String, Object>();
            }
            properties.put(methodName, value);
        }
    }

    private static class SafeMethodInvoker implements MethodInvoker {
        private final MethodInvoker next;

        private SafeMethodInvoker(MethodInvoker next) {
            this.next = next;
        }

        @Override
        public void invoke(MethodInvocation invocation) throws Throwable {
            next.invoke(invocation);
            if (invocation.found() || invocation.getParameterTypes().length != 1 || !invocation.isIsOrGet()) {
                return;
            }

            MethodInvocation getterInvocation = new MethodInvocation(invocation.getName(), invocation.getReturnType(), invocation.getGenericReturnType(), EMPTY_CLASS_ARRAY, invocation.getView(), invocation.getViewType(), invocation.getDelegate(), EMPTY);
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

        @Override
        public void invoke(MethodInvocation invocation) throws Throwable {
            next.invoke(invocation);
            if (invocation.found()) {
                return;
            }

            String methodName = invocation.getName();
            boolean isSupportMethod = methodName.length() > 11 && methodName.startsWith("is") && methodName.endsWith("Supported");
            if (!isSupportMethod) {
                return;
            }

            String getterName = "get" + methodName.substring(2, methodName.length() - 9);
            MethodInvocation getterInvocation = new MethodInvocation(getterName, invocation.getReturnType(), invocation.getGenericReturnType(), EMPTY_CLASS_ARRAY, invocation.getView(), invocation.getViewType(), invocation.getDelegate(), EMPTY);
            next.invoke(getterInvocation);
            invocation.setResult(getterInvocation.found());
        }
    }

    private static class BeanMixInMethodInvoker implements MethodInvoker {
        private final Object instance;
        private final MethodInvoker next;

        BeanMixInMethodInvoker(Object instance, MethodInvoker next) {
            this.instance = instance;
            this.next = next;
        }

        @Override
        public void invoke(MethodInvocation invocation) throws Throwable {
            MethodInvocation beanInvocation = new MethodInvocation(invocation.getName(), invocation.getReturnType(), invocation.getGenericReturnType(), invocation.getParameterTypes(), invocation.getView(), invocation.getViewType(), instance, invocation.getParameters());
            next.invoke(beanInvocation);
            if (beanInvocation.found()) {
                invocation.setResult(beanInvocation.getResult());
                return;
            }
            if (!invocation.isGetter()) {
                return;
            }

            beanInvocation = new MethodInvocation(invocation.getName(), invocation.getReturnType(), invocation.getGenericReturnType(), new Class<?>[]{invocation.getViewType()}, invocation.getView(), invocation.getViewType(), instance, new Object[]{invocation.getView()});
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
        private final ThreadLocal<MethodInvocation> current = new ThreadLocal<>();

        ClassMixInMethodInvoker(Class<?> mixInClass, MethodInvoker next) {
            this.mixInClass = mixInClass;
            this.next = next;
        }

        @Override
        public void invoke(MethodInvocation invocation) throws Throwable {
            if (current.get() != null) {
                // Already invoking a method on the mix-in
                return;
            }

            if (instance == null) {
                instance = DirectInstantiator.INSTANCE.newInstance(mixInClass, invocation.getView());
            }
            MethodInvocation beanInvocation = new MethodInvocation(invocation.getName(), invocation.getReturnType(), invocation.getGenericReturnType(), invocation.getParameterTypes(), invocation.getView(), invocation.getViewType(), instance, invocation.getParameters());
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

    private interface ViewDecoration {
        void collectInvokers(Object sourceObject, Class<?> viewType, List<MethodInvoker> invokers);

        boolean isNoOp();

        /**
         * Filter this decoration to apply only to the given view types. Return {@link #NO_OP_MAPPER} if this decoration does not apply to any of the types.
         */
        ViewDecoration restrictTo(Set<Class<?>> viewTypes);
    }

    private static class NoOpDecoration implements ViewDecoration, Serializable {
        @Override
        public void collectInvokers(Object sourceObject, Class<?> viewType, List<MethodInvoker> invokers) {
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof NoOpDecoration;
        }

        @Override
        public int hashCode() {
            return 0;
        }

        @Override
        public boolean isNoOp() {
            return true;
        }

        @Override
        public ViewDecoration restrictTo(Set<Class<?>> viewTypes) {
            return this;
        }
    }

    private static class MixInMappingAction implements ViewDecoration, Serializable {
        private final List<? extends ViewDecoration> decorations;

        private MixInMappingAction(List<? extends ViewDecoration> decorations) {
            assert decorations.size() >= 2;
            this.decorations = decorations;
        }

        static ViewDecoration chain(List<? extends ViewDecoration> decorations) {
            if (decorations.isEmpty()) {
                return NO_OP_MAPPER;
            }
            if (decorations.size() == 1) {
                return decorations.get(0);
            }
            return new MixInMappingAction(decorations);
        }

        @Override
        public int hashCode() {
            int v = 0;
            for (ViewDecoration decoration : decorations) {
                v = v ^ decoration.hashCode();
            }
            return v;
        }

        @Override
        public boolean equals(Object obj) {
            if (!obj.getClass().equals(MixInMappingAction.class)) {
                return false;
            }
            MixInMappingAction other = (MixInMappingAction) obj;
            return decorations.equals(other.decorations);
        }

        @Override
        public boolean isNoOp() {
            for (ViewDecoration decoration : decorations) {
                if (!decoration.isNoOp()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public ViewDecoration restrictTo(Set<Class<?>> viewTypes) {
            List<ViewDecoration> filtered = new ArrayList<ViewDecoration>();
            for (ViewDecoration viewDecoration : decorations) {
                ViewDecoration filteredDecoration = viewDecoration.restrictTo(viewTypes);
                if (!filteredDecoration.isNoOp()) {
                    filtered.add(filteredDecoration);
                }
            }
            if (filtered.size() == 0) {
                return NO_OP_MAPPER;
            }
            if (filtered.size() == 1) {
                return filtered.get(0);
            }
            if (filtered.equals(decorations)) {
                return this;
            }
            return new MixInMappingAction(filtered);
        }

        @Override
        public void collectInvokers(Object sourceObject, Class<?> viewType, List<MethodInvoker> invokers) {
            for (ViewDecoration decoration : decorations) {
                decoration.collectInvokers(sourceObject, viewType, invokers);
            }
        }
    }

    private static abstract class TypeSpecificMappingAction implements ViewDecoration, Serializable {
        protected final Class<?> targetType;

        TypeSpecificMappingAction(Class<?> targetType) {
            this.targetType = targetType;
        }

        @Override
        public boolean isNoOp() {
            return false;
        }

        @Override
        public ViewDecoration restrictTo(Set<Class<?>> viewTypes) {
            if (viewTypes.contains(targetType)) {
                return this;
            }
            return NO_OP_MAPPER;
        }

        @Override
        public void collectInvokers(Object sourceObject, Class<?> viewType, List<MethodInvoker> invokers) {
            if (targetType.isAssignableFrom(viewType)) {
                invokers.add(createInvoker());
            }
        }

        protected abstract MethodInvoker createInvoker();
    }

    private static class MixInBeanMappingAction extends TypeSpecificMappingAction {
        private final Object mixIn;

        MixInBeanMappingAction(Class<?> targetType, Object mixIn) {
            super(targetType);
            this.mixIn = mixIn;
        }

        @Override
        public int hashCode() {
            return targetType.hashCode() ^ mixIn.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (!obj.getClass().equals(MixInBeanMappingAction.class)) {
                return false;
            }
            MixInBeanMappingAction other = (MixInBeanMappingAction) obj;
            return targetType.equals(other.targetType) && mixIn.equals(other.mixIn);
        }

        @Override
        protected MethodInvoker createInvoker() {
            return new BeanMixInMethodInvoker(mixIn, REFLECTION_METHOD_INVOKER);
        }
    }

    private static class MixInTypeMappingAction extends TypeSpecificMappingAction {
        private final Class<?> mixInType;

        MixInTypeMappingAction(Class<?> targetType, Class<?> mixInType) {
            super(targetType);
            this.mixInType = mixInType;
        }

        @Override
        public int hashCode() {
            return targetType.hashCode() ^ mixInType.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (!obj.getClass().equals(MixInTypeMappingAction.class)) {
                return false;
            }
            MixInTypeMappingAction other = (MixInTypeMappingAction) obj;
            return targetType.equals(other.targetType) && mixInType.equals(other.mixInType);
        }

        @Override
        protected MethodInvoker createInvoker() {
            return new ClassMixInMethodInvoker(mixInType, REFLECTION_METHOD_INVOKER);
        }
    }

    private class DefaultViewBuilder<T> implements ViewBuilder<T> {
        private final Class<T> viewType;
        @Nullable
        private final ViewGraphDetails graphDetails;
        List<ViewDecoration> viewDecorations = new ArrayList<ViewDecoration>();

        DefaultViewBuilder(Class<T> viewType) {
            this.viewType = viewType;
            this.graphDetails = null;
        }

        DefaultViewBuilder(Class<T> viewType, @Nullable ViewGraphDetails graphDetails) {
            this.viewType = viewType;
            this.graphDetails = graphDetails;
        }

        @Override
        public ViewBuilder<T> mixInTo(final Class<?> targetType, final Object mixIn) {
            viewDecorations.add(new MixInBeanMappingAction(targetType, mixIn));
            return this;
        }

        @Override
        public ViewBuilder<T> mixInTo(final Class<?> targetType, final Class<?> mixInType) {
            viewDecorations.add(new MixInTypeMappingAction(targetType, mixInType));
            return this;
        }

        @Override
        public T build(@Nullable final Object sourceObject) {
            if (sourceObject == null) {
                return null;
            }

            ViewDecoration viewDecoration = MixInMappingAction.chain(viewDecorations);
            return createView(viewType, sourceObject, viewDecoration, graphDetails != null ? graphDetails : new ViewGraphDetails(targetTypeProvider));
        }
    }
}
