/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.internal.service;

import org.gradle.api.Action;
import org.gradle.internal.Factory;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.concurrent.Stoppable;

import java.io.Closeable;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Formatter;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A hierarchical {@link ServiceRegistry} implementation.
 *
 * <p>Subclasses can register services by:</p>
 *
 * <li>Calling {@link #add(Class, Object)} to register a service instance.</li>
 *
 * <li>Calling {@link #addProvider(Object)} to register a service provider bean. A provider bean may have factory, decorator and configuration methods as described below.</li>
 *
 * <li>Adding a factory method. A factory method should have a name that starts with 'create', and have a non-void return type. For example, <code>protected SomeService createSomeService() { ....
 * }</code>. Parameters are injected using services from this registry or its parents. Parameter of type {@link ServiceRegistry} will receive the service registry that owns the service. Parameter of
 * type {@code List<T>} will receive all services of type T, if any. Note that factory methods with a single parameter and an return type equal to that parameter type are interpreted as decorator
 * methods.</li>
 *
 * <li>Adding a decorator method. A decorator method should have a name that starts with 'decorate', take a single parameter, and a have return type equal to the parameter type. Before invoking the
 * method, the parameter is located in the parent service registry and then passed to the method.</li>
 *
 * <li>Adding a configure method. A configure method should be called 'configure', take a {@link ServiceRegistration} parameter, and a have a void return type. Additional parameters are injected using
 * services from this registry or its parents.</li>
 *
 * </ul>
 *
 * <p>Service instances are created on demand. {@link #getFactory(Class)} looks for a service instance which implements {@code Factory<T>} where {@code T} is the expected type.</p>
 *
 * <p>Service instances and factories are closed when the registry that created them is closed using {@link #close()}. If a service instance or factory implements {@link java.io.Closeable} or {@link
 * org.gradle.internal.concurrent.Stoppable} then the appropriate close() or stop() method is called. Instances are closed in reverse dependency order.</p>
 *
 * <p>Service registries are arranged in a hierarchy. If a service of a given type cannot be located, the registry uses its parent registry, if any, to locate the service.</p>
 */
public class DefaultServiceRegistry implements ServiceRegistry, Closeable {
    private enum State {INIT, IN_USE, CLOSING, CLOSED};
    private final static ServiceRegistry[] NO_PARENTS = new ServiceRegistry[0];
    private final static ServiceProvider[] NO_DEPENDENTS = new ServiceProvider[0];
    private final static Object[] NO_PARAMS = new Object[0];

    private final OwnServices ownServices;
    private final Provider allServices;
    private final Provider parentServices;
    private final String displayName;
    private final Provider thisAsProvider;

    private volatile State state = State.INIT;
    private final Object stopLock = new Object();
    private final AtomicInteger inProgress = new AtomicInteger(0);


    public DefaultServiceRegistry() {
        this(null, NO_PARENTS);
    }

    public DefaultServiceRegistry(String displayName) {
        this(displayName, NO_PARENTS);
    }

    public DefaultServiceRegistry(ServiceRegistry... parents) {
        this(null, parents);
    }

    public DefaultServiceRegistry(String displayName, ServiceRegistry... parents) {
        this.displayName = displayName;
        this.ownServices = new OwnServices();
        this.thisAsProvider = new ParentServices(this);
        if (parents.length == 0) {
            this.parentServices = null;
            this.allServices = ownServices;
        } else {
            parentServices = setupParentServices(parents);
            allServices = new CompositeProvider(ownServices, parentServices);
        }

        findProviderMethods(this);
    }

    private static Provider setupParentServices(ServiceRegistry[] parents) {
        Provider parentServices;
        if (parents.length == 1) {
            parentServices = toParentServices(parents[0]);
        } else {
            Provider[] parentProviders = new Provider[parents.length];
            for (int i = 0; i < parents.length; i++) {
                parentProviders[i] = toParentServices(parents[i]);
            }
            parentServices = new CompositeProvider(parentProviders);
        }
        return parentServices;
    }

    private Provider asProvider() {
        return thisAsProvider;
    }

    private static Provider toParentServices(ServiceRegistry serviceRegistry) {
        if (serviceRegistry instanceof DefaultServiceRegistry) {
            return ((DefaultServiceRegistry) serviceRegistry).asProvider();
        }
        return new ParentServices(serviceRegistry);
    }

    /**
     * Creates a service registry that uses the given providers.
     */
    public static ServiceRegistry create(Object... providers) {
        DefaultServiceRegistry registry = new DefaultServiceRegistry();
        for (Object provider : providers) {
            registry.addProvider(provider);
        }
        return registry;
    }

    private String getDisplayName() {
        return displayName == null ? getClass().getSimpleName() : displayName;
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    private void findProviderMethods(Object target) {
        Class<?> type = target.getClass();
        RelevantMethods methods = RelevantMethods.getMethods(type);
        for (ServiceMethod method : methods.decorators) {
            if (parentServices == null) {
                throw new ServiceLookupException(String.format("Cannot use decorator method %s.%s() when no parent registry is provided.", type.getSimpleName(), method.getName()));
            }
            ownServices.add(new DecoratorMethodService(this, target, method));
        }
        for (ServiceMethod method : methods.factories) {
            ownServices.add(new FactoryMethodService(this, target, method));
        }
        for (ServiceMethod method : methods.configurers) {
            applyConfigureMethod(method, target);
        }
    }

    private void applyConfigureMethod(ServiceMethod method, Object target) {
        Object[] params = new Object[method.getParameterTypes().length];
        for (int i = 0; i < method.getParameterTypes().length; i++) {
            Type paramType = method.getParameterTypes()[i];
            if (paramType.equals(ServiceRegistration.class)) {
                params[i] = newRegistration();
            } else {
                ServiceProvider paramProvider = find(paramType, allServices);
                if (paramProvider == null) {
                    throw new ServiceLookupException(String.format("Cannot configure services using %s.%s() as required service of type %s is not available.",
                        method.getOwner().getSimpleName(),
                        method.getName(),
                        format(paramType)));
                }
                params[i] = paramProvider.get();
            }
        }
        try {
            method.invoke(target, params);
        } catch (Exception e) {
            throw new ServiceLookupException(String.format("Could not configure services using %s.%s().",
                method.getOwner().getSimpleName(),
                method.getName()), e);
        }
    }

    /**
     * Adds services to this container using the given action.
     */
    public void register(Action<? super ServiceRegistration> action) {
        assertMutable();
        action.execute(newRegistration());
    }

    private void assertMutable() {
        if (state != State.INIT) {
            throw new IllegalStateException("Cannot add provide to service registry " + this + " as it is no longer mutable");
        }
    }

    private ServiceRegistration newRegistration() {
        return new ServiceRegistration() {
            public <T> void add(Class<T> serviceType, T serviceInstance) {
                DefaultServiceRegistry.this.add(serviceType, serviceInstance);
            }

            public void add(Class<?> serviceType) {
                ownServices.add(new ConstructorService(DefaultServiceRegistry.this, serviceType));
            }

            public void addProvider(Object provider) {
                DefaultServiceRegistry.this.addProvider(provider);
            }
        };
    }

    /**
     * Adds a service to this registry. The given object is closed when this registry is closed.
     */
    public <T> DefaultServiceRegistry add(Class<T> serviceType, final T serviceInstance) {
        assertMutable();
        ownServices.add(new FixedInstanceService<T>(this, serviceType, serviceInstance));
        return this;
    }

    /**
     * Adds a service provider bean to this registry. This provider may define factory and decorator methods.
     */
    public DefaultServiceRegistry addProvider(Object provider) {
        assertMutable();
        findProviderMethods(provider);
        return this;
    }

    /**
     * Closes all services for this registry. For each service, if the service has a public void close() or stop() method, that method is called to close the service.
     */
    public void close() {
        if (state == State.CLOSED || state == State.CLOSING) {
            return;
        }

        synchronized (stopLock) {
            if (state == State.CLOSED || state == State.CLOSING) {
                return;
            }
            state = State.CLOSING;
            waitForPendingRequests();
            CompositeStoppable.stoppable(allServices).stop();
            state = State.CLOSED;
        }
    }

    private void waitForPendingRequests() {
        while (inProgress.get() != 0) {
            try {
                stopLock.wait();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void newRequestInProgress() {
        noLongerMutable();
        if (state == State.CLOSED) {
            throw new IllegalStateException(String.format("%s has been closed.", getDisplayName()));
        }
        inProgress.incrementAndGet();
    }

    private void noLongerMutable() {
        if (state == State.INIT) {
            state = State.IN_USE;
            ownServices.noLongerMutable();
        }
    }

    private void requestFinished() {
        boolean noMoreRequests = inProgress.decrementAndGet() == 0;
        if (noMoreRequests && state == State.CLOSING) {
            synchronized (stopLock) {
                stopLock.notify();
            }
        }
    }
    public boolean isClosed() {
        return state == State.CLOSED;
    }

    private static String format(Type type) {
        if (type instanceof Class) {
            Class<?> aClass = (Class) type;
            return aClass.getSimpleName();
        } else if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            StringBuilder builder = new StringBuilder();
            builder.append(format(parameterizedType.getRawType()));
            builder.append("<");
            for (int i = 0; i < parameterizedType.getActualTypeArguments().length; i++) {
                Type typeParam = parameterizedType.getActualTypeArguments()[i];
                if (i > 0) {
                    builder.append(", ");
                }
                builder.append(format(typeParam));
            }
            builder.append(">");
            return builder.toString();
        }

        return type.toString();
    }


    public <T> T get(Class<T> serviceType) throws UnknownServiceException, ServiceLookupException {
        return serviceType.cast(get((Type) serviceType));
    }

    public Object get(Type serviceType) throws UnknownServiceException, ServiceLookupException {
        assertValidServiceType(unwrap(serviceType));
        Object instance = doGet(serviceType);
        if (instance == null) {
            throw new UnknownServiceException(serviceType, String.format("No service of type %s available in %s.", format(serviceType), getDisplayName()));
        }
        return instance;
    }

    private Object doGet(Type serviceType) {
        try {
            newRequestInProgress();
            ServiceProvider provider = find(serviceType, allServices);
            return provider == null ? null : provider.get();
        } finally {
            requestFinished();
        }
    }
    private ServiceProvider getProvider(Type serviceType) {
        try {
            newRequestInProgress();
            return find(serviceType, allServices);
        } finally {
            requestFinished();
        }
    }

    public <T> Factory<T> getFactory(Class<T> type) {
        assertValidServiceType(type);
        Factory<T> factory = doGetFactory(type);
        if (factory == null) {
            throw new UnknownServiceException(type, String.format("No factory for objects of type %s available in %s.", format(type), getDisplayName()));
        }
        return factory;
    }

    private <T> Factory<T> doGetFactory(Class<T> type) {
        try {
            newRequestInProgress();
            ServiceProvider provider = allServices.getFactory(type);
            return provider == null ? null : (Factory<T>) provider.get();
        } finally {
            requestFinished();
        }
    }

    private ServiceProvider getFactoryProvider(Class<?> serviceType) {
        try {
            newRequestInProgress();
            return allServices.getFactory(serviceType);
        } finally {
            requestFinished();
        }
    }

    public <T> List<T> getAll(Class<T> serviceType) throws ServiceLookupException {
        assertValidServiceType(serviceType);
        List<T> services = new ArrayList<T>();
        collectInstances(serviceType, services);
        return services;
    }

    private <T> void collectInstances(Class<T> serviceType, List<T> results) {
        try {
            newRequestInProgress();
            allServices.getAll(serviceType, new InstanceUnpackingList<T>(serviceType, results));
        } finally {
            requestFinished();
        }
    }

    private <T> void collectProviders(Class<T> serviceType, List<ServiceProvider> results) {
        try {
            newRequestInProgress();
            allServices.getAll(serviceType, results);
        } finally {
            requestFinished();
        }
    }

    private static class InstanceUnpackingList<T> extends AbstractList<ServiceProvider> {
        private final Class<T> serviceType;
        private final List<T> delegate;

        private InstanceUnpackingList(Class<T> serviceType, List<T> delegate) {
            this.serviceType = serviceType;
            this.delegate = delegate;
        }

        @Override
        public ServiceProvider get(int index) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean add(ServiceProvider provider) {
            return delegate.add(serviceType.cast(provider.get()));
        }

        @Override
        public int size() {
            return delegate.size();
        }
    }

    public <T> T newInstance(Class<T> type) {
        return getFactory(type).create();
    }

    /**
     * Provides a single service instance.
     */
    interface ServiceProvider {
        String getDisplayName();

        Object get();

        void requiredBy(Provider provider);
    }

    /**
     * Provides a set or zero or more services. The get-methods may be called concurrently. {@link #stop()} is guaranteed to be only called once,
     * after all get-methods have completed.
     */
    interface Provider extends Stoppable {
        /**
         * Locates a service instance of the given type. Returns null if this provider does not provide a service of this type.
         */
        ServiceProvider getService(Type serviceType);

        /**
         * Locates a factory for services of the given type. Returns null if this provider does not provide any services of this type.
         */
        ServiceProvider getFactory(Class<?> type);

        /**
         * Collects all services of the given type.
         */
        void getAll(Class<?> serviceType, List<ServiceProvider> result);
    }

    private class OwnServices implements Provider {
        private final Map<Class<?>, Object> providersByType = new IdentityHashMap<Class<?>, Object>();
        private final CompositeStoppable stoppable = CompositeStoppable.stoppable();
        private ProviderAnalyser analyser = new ProviderAnalyser();

        @Override
        public ServiceProvider getFactory(Class<?> type) {
            Object providerCandidates = providersByType.get(Factory.class);
            if (providerCandidates == null) {
                return null;
            }
            if (providerCandidates instanceof Provider) {
                return ((Provider) providerCandidates).getFactory(type);
            }
            List<Provider> providers = (List<Provider>) providerCandidates;
            if (providers.isEmpty()) {
                return null;
            }
            if (providers.size() == 1) {
                return providers.get(0).getFactory(type);
            }

            List<ServiceProvider> services = new ArrayList<ServiceProvider>(providers.size());
            for (Provider provider : providers) {
                ServiceProvider service = provider.getFactory(type);
                if (service != null) {
                    services.add(service);
                }
            }

            if (services.isEmpty()) {
                return null;
            }
            if (services.size() == 1) {
                return services.get(0);
            }

            Set<String> descriptions = new TreeSet<String>();
            for (ServiceProvider candidate : services) {
                descriptions.add(candidate.getDisplayName());
            }

            Formatter formatter = new Formatter();
            formatter.format("Multiple factories for objects of type %s available in %s:", format(type), getDisplayName());
            for (String description : descriptions) {
                formatter.format("%n   - %s", description);
            }
            throw new ServiceLookupException(formatter.toString());
        }

        @Override
        public ServiceProvider getService(Type type) {
            Object providerCandidates = providersByType.get(unwrap(type));
            if (providerCandidates == null) {
                return null;
            }
            if (providerCandidates instanceof Provider) {
                return ((Provider) providerCandidates).getService(type);
            }
            List<Provider> providers = (List<Provider>) providerCandidates;

            List<ServiceProvider> services = new ArrayList<ServiceProvider>(providers.size());
            for (Provider provider : providers) {
                ServiceProvider service = provider.getService(type);
                if (service != null) {
                    services.add(service);
                }
            }

            if (services.isEmpty()) {
                return null;
            }
            if (services.size() == 1) {
                return services.get(0);
            }

            Set<String> descriptions = new TreeSet<String>();
            for (ServiceProvider candidate : services) {
                descriptions.add(candidate.getDisplayName());
            }

            Formatter formatter = new Formatter();
            formatter.format("Multiple services of type %s available in %s:", format(type), getDisplayName());
            for (String description : descriptions) {
                formatter.format("%n   - %s", description);
            }
            throw new ServiceLookupException(formatter.toString());
        }

        @Override
        public void getAll(Class<?> serviceType, List<ServiceProvider> result) {
            Object providerCandidates = providersByType.get(serviceType);
            if (providerCandidates == null) {
                return;
            }
            if (providerCandidates instanceof Provider) {
                ((Provider) providerCandidates).getAll(serviceType, result);
                return;
            }
            List<Provider> providers = (List<Provider>) providerCandidates;
            for (Provider provider : providers) {
                provider.getAll(serviceType, result);
            }
        }

        @Override
        public void stop() {
            stoppable.stop();
        }

        public void add(Provider provider) {
            assertMutable();
            if (!(provider instanceof SingletonService)) {
                throw new UnsupportedOperationException("Unsupported service provider type: " + provider);
            }
            stoppable.add(provider);
            analyser.addProviderForClassHierarchy(((SingletonService) provider).serviceClass, provider);
        }

        public void noLongerMutable() {
            analyser = null;
        }

        private class ProviderAnalyser {
            private Set<Class<?>> seen = Collections.newSetFromMap(new IdentityHashMap<Class<?>, Boolean>());

            public void addProviderForClassHierarchy(Class<?> serviceType, Provider provider) {
                analyseType(serviceType, provider);
                seen.clear();
            }

            private void analyseType(Class<?> type, Provider provider) {
                if (type == null || type == Object.class) {
                    return;
                }
                if (seen.add(type)) {
                    putServiceType(type, provider);
                    analyseType(type.getSuperclass(), provider);
                    for (Class<?> iface : type.getInterfaces()) {
                        analyseType(iface, provider);
                    }
                }
            }

            private void putServiceType(Class<?> type, Provider provider) {
                Object existing = providersByType.get(type);
                if (existing == null) {
                    providersByType.put(type, provider);
                } else if (existing instanceof  Provider){
                    List<Provider> providers = new ArrayList<Provider>(2);
                    providers.add((Provider) existing);
                    providers.add(provider);
                    providersByType.put(type, providers);
                } else {
                    ((List<Provider>) existing).add(provider);
                }
            }
        }
    }

    private static Class<?> unwrap(Type type) {
        if (type instanceof Class) {
            return (Class) type;
        } else {
            if (type instanceof WildcardType) {
                final WildcardType wildcardType = (WildcardType) type;
                if (wildcardType.getUpperBounds()[0] instanceof Class && wildcardType.getLowerBounds().length == 0) {
                    return (Class<?>) wildcardType.getUpperBounds()[0];
                }
            }
            ParameterizedType parameterizedType = (ParameterizedType) type;
            return (Class) parameterizedType.getRawType();
        }
    }

    private static abstract class ManagedObjectProvider<T> implements Provider {
        protected final DefaultServiceRegistry owner;
        private final Queue<Provider> dependents = new ConcurrentLinkedQueue<Provider>();
        private volatile T instance;

        protected ManagedObjectProvider(DefaultServiceRegistry owner) {
            this.owner = owner;
        }

        protected final void setInstance(T instance) {
            this.instance = instance;
        }

        public final T getInstance() {
            if (instance == null) {
                synchronized (this) {
                    if (instance == null) {
                        instance = create();
                        assert instance != null : String.format("create() of %s returned null", toString());
                    }
                }
            }
            return instance;
        }

        /**
         * Subclasses implement this method to create the service instance. It is never called concurrently and may not return null.
         */
        protected abstract T create();

        public final void requiredBy(Provider provider) {
            if (fromSameRegistry(provider)) {
                dependents.add(provider);
            }
        }

        private boolean fromSameRegistry(Provider provider) {
            return provider instanceof ManagedObjectProvider && ((ManagedObjectProvider) provider).owner == owner;
        }

        public final synchronized void stop() {
            try {
                if (instance != null) {
                    CompositeStoppable.stoppable(dependents).add(instance).stop();
                }
            } finally {
                dependents.clear();
                instance = null;
            }
        }
    }

    private static abstract class SingletonService extends ManagedObjectProvider<Object> implements ServiceProvider {
        private enum BindState {UNBOUND, BINDING, BOUND}

        final Type serviceType;
        final Class serviceClass;

        volatile BindState state = BindState.UNBOUND;
        Class factoryElementType;

        SingletonService(DefaultServiceRegistry owner, Type serviceType) {
            super(owner);
            this.serviceType = serviceType;
            serviceClass = unwrap(serviceType);
        }

        @Override
        public String toString() {
            return getDisplayName();
        }

        @Override
        public Object get() {
            return getInstance();
        }

        private ServiceProvider prepare() {
            if (state == BindState.BOUND) {
                return this;
            }
            synchronized (this) {
                if (state == BindState.BINDING) {
                    throw new ServiceValidationException("This service depends on itself");
                }
                if (state == BindState.UNBOUND) {
                    state = BindState.BINDING;
                    try {
                        bind();
                        state = BindState.BOUND;
                    } catch (RuntimeException e) {
                        state = BindState.UNBOUND;
                        throw e;
                    }
                }
                return this;
            }
        }

        /**
         * Do any preparation work and validation to ensure that {@link #create()} ()} can be called later.
         * This method is never called concurrently.
         */
        protected void bind() {
        }

        @Override
        public ServiceProvider getService(Type serviceType) {
            if (!isSatisfiedBy(serviceType, this.serviceType)) {
                return null;
            }
            return prepare();
        }

        @Override
        public void getAll(Class<?> serviceType, List<ServiceProvider> result) {
            if (serviceType.isAssignableFrom(this.serviceClass)) {
                result.add(prepare());
            }
        }

        @Override
        public ServiceProvider getFactory(Class<?> elementType) {
            if (!isFactory(serviceType, elementType)) {
                return null;
            }
            return prepare();
        }

        private boolean isFactory(Type type, Class<?> elementType) {
            Class c = unwrap(type);
            if (!Factory.class.isAssignableFrom(c)) {
                return false;
            }
            if (factoryElementType != null) {
                return elementType.isAssignableFrom(factoryElementType);
            }

            if (type instanceof ParameterizedType) {
                // Check if type is Factory<? extends ElementType>
                ParameterizedType parameterizedType = (ParameterizedType) type;
                if (parameterizedType.getRawType().equals(Factory.class)) {
                    Type actualType = parameterizedType.getActualTypeArguments()[0];
                    if (actualType instanceof Class) {
                        factoryElementType = (Class) actualType;
                        return elementType.isAssignableFrom((Class<?>) actualType);
                    }
                }
            }

            // Check if type extends Factory<? extends ElementType>
            for (Type interfaceType : c.getGenericInterfaces()) {
                if (isFactory(interfaceType, elementType)) {
                    return true;
                }
            }

            return false;
        }
    }

    private static abstract class FactoryService extends SingletonService {
        private ServiceProvider[] paramProviders;

        protected FactoryService(DefaultServiceRegistry owner, Type serviceType) {
            super(owner, serviceType);
        }

        protected abstract Type[] getParameterTypes();

        protected abstract Member getFactory();

        @Override
        protected void bind() {
            Type[] parameterTypes = getParameterTypes();
            if (parameterTypes.length == 0) {
                paramProviders = NO_DEPENDENTS;
                return;
            }
            paramProviders = new ServiceProvider[parameterTypes.length];
            for (int i = 0; i < parameterTypes.length; i++) {
                Type paramType = parameterTypes[i];
                if (paramType.equals(ServiceRegistry.class)) {
                    paramProviders[i] = owner.getThisAsService();
                } else {
                    ServiceProvider paramProvider;
                    try {
                        paramProvider = owner.find(paramType, owner.allServices);
                    } catch (ServiceLookupException e) {
                        throw new ServiceCreationException(String.format("Cannot create service of type %s using %s.%s() as there is a problem with parameter #%s of type %s.",
                            format(serviceType),
                            getFactory().getDeclaringClass().getSimpleName(),
                            getFactory().getName(),
                            i + 1,
                            format(paramType)), e);
                    }
                    if (paramProvider == null) {
                        throw new ServiceCreationException(String.format("Cannot create service of type %s using %s.%s() as required service of type %s is not available.",
                            format(serviceType),
                            getFactory().getDeclaringClass().getSimpleName(),
                            getFactory().getName(),
                            format(paramType)));

                    }
                    paramProviders[i] = paramProvider;
                    paramProvider.requiredBy(this);
                }
            }
        }

        @Override
        protected Object create() {
            Object[] params = assembleParameters();
            Object result = invokeMethod(params);
            // Can discard the state required to create instance
            paramProviders = null;
            return result;
        }

        private Object[] assembleParameters() {
            if (paramProviders == NO_DEPENDENTS) {
                return NO_PARAMS;
            }
            Object[] params = new Object[paramProviders.length];
            for (int i = 0; i < paramProviders.length; i++) {
                ServiceProvider paramProvider = paramProviders[i];
                params[i] = paramProvider.get();
            }
            return params;
        }

        protected abstract Object invokeMethod(Object[] params);
    }

    private class FactoryMethodService extends FactoryService {
        private final ServiceMethod method;
        private Object target;

        public FactoryMethodService(DefaultServiceRegistry owner, Object target, ServiceMethod method) {
            super(owner, method.getServiceType());
            this.target = target;
            this.method = method;
        }

        public String getDisplayName() {
            return "Service " + format(method.getServiceType()) + " at " + method.getOwner().getSimpleName() + "." + method.getName() + "()";
        }

        protected Type[] getParameterTypes() {
            return method.getParameterTypes();
        }

        @Override
        protected Member getFactory() {
            return method.getMethod();
        }

        protected Object invokeMethod(Object[] params) {
            Object result;
            try {
                result = method.invoke(target, params);
            } catch (Exception e) {
                throw new ServiceCreationException(String.format("Could not create service of type %s using %s.%s().",
                    format(serviceType),
                    method.getOwner().getSimpleName(),
                    method.getName()),
                    e);
            }
            try {
                if (result == null) {
                    throw new ServiceCreationException(String.format("Could not create service of type %s using %s.%s() as this method returned null.",
                        format(serviceType),
                        method.getOwner().getSimpleName(),
                        method.getName()));
                }
                return result;
            } finally {
                // Can discard the state required to create instance
                target = null;
            }
        }
    }

    private ServiceProvider getThisAsService() {
        return new ServiceProvider() {
            public String getDisplayName() {
                return "ServiceRegistry " + DefaultServiceRegistry.this.getDisplayName();
            }

            public Object get() {
                return DefaultServiceRegistry.this;
            }

            public void requiredBy(Provider provider) {
            }
        };
    }

    private static class FixedInstanceService<T> extends SingletonService {
        public FixedInstanceService(DefaultServiceRegistry owner, Class<T> serviceType, T serviceInstance) {
            super(owner, serviceType);
            setInstance(serviceInstance);
        }

        public String getDisplayName() {
            return "Service " + format(serviceType) + " with implementation " + getInstance();
        }

        @Override
        protected Object create() {
            throw new UnsupportedOperationException();
        }
    }

    private static class ConstructorService extends FactoryService {
        private final Constructor<?> constructor;

        private ConstructorService(DefaultServiceRegistry owner, Class<?> serviceType) {
            super(owner, serviceType);
            Constructor<?>[] constructors = serviceType.getDeclaredConstructors();
            if (constructors.length != 1) {
                throw new ServiceValidationException(String.format("Expected a single constructor for %s.", format(serviceType)));
            }
            this.constructor = constructors[0];
        }

        @Override
        protected Type[] getParameterTypes() {
            return constructor.getGenericParameterTypes();
        }

        @Override
        protected Member getFactory() {
            return constructor;
        }

        @Override
        protected Object invokeMethod(Object[] params) {
            try {
                return constructor.newInstance(params);
            } catch (InvocationTargetException e) {
                throw new ServiceCreationException(String.format("Could not create service of type %s.", format(serviceType)), e.getCause());
            } catch (Exception e) {
                throw new ServiceCreationException(String.format("Could not create service of type %s.", format(serviceType)), e);
            }
        }

        public String getDisplayName() {
            return "Service " + format(serviceType);
        }
    }

    private static class DecoratorMethodService extends SingletonService {
        private final ServiceMethod method;
        private Object target;
        private ServiceProvider paramProvider;

        public DecoratorMethodService(DefaultServiceRegistry owner, Object target, ServiceMethod method) {
            super(owner, method.getServiceType());
            this.target = target;
            this.method = method;
        }

        public String getDisplayName() {
            return "Service " + format(method.getServiceType()) + " at " + method.getOwner().getSimpleName() + "." + method.getName() + "()";
        }

        @Override
        protected void bind() {
            Type paramType = method.getParameterTypes()[0];
            paramProvider = owner.find(paramType, owner.parentServices);
            if (paramProvider == null) {
                throw new ServiceCreationException(String.format("Cannot create service of type %s using %s.%s() as required service of type %s is not available in parent registries.",
                    format(method.getServiceType()),
                    method.getOwner().getSimpleName(),
                    method.getName(),
                    format(paramType)));
            }
        }

        @Override
        protected Object create() {
            Object param = paramProvider.get();
            Object result;
            try {
                result = method.invoke(target, param);
            } catch (Exception e) {
                throw new ServiceCreationException(String.format("Could not create service of type %s using %s.%s().",
                    format(method.getServiceType()),
                    method.getOwner().getSimpleName(),
                    method.getName()),
                    e);
            }
            try {
                if (result == null) {
                    throw new ServiceCreationException(String.format("Could not create service of type %s using %s.%s() as this method returned null.",
                        format(method.getServiceType()),
                        method.getOwner().getSimpleName(),
                        method.getName()));
                }
                return result;
            } finally {
                // Can discard state required to create instance
                paramProvider = null;
                target = null;
            }
        }
    }

    private static class CompositeProvider implements Provider {
        private final Provider[] providers;

        private CompositeProvider(Provider... providers) {
            this.providers = providers;
        }

        @Override
        public ServiceProvider getService(Type serviceType) {
            for (Provider provider : providers) {
                ServiceProvider service = provider.getService(serviceType);
                if (service != null) {
                    return service;
                }
            }
            return null;
        }

        @Override
        public ServiceProvider getFactory(Class<?> type) {
            for (Provider provider : providers) {
                ServiceProvider factory = provider.getFactory(type);
                if (factory != null) {
                    return factory;
                }
            }
            return null;
        }

        @Override
        public void getAll(Class<?> serviceType, List<ServiceProvider> result) {
            for (Provider provider : providers) {
                provider.getAll(serviceType, result);
            }
        }

        @Override
        public void stop() {
            try {
                CompositeStoppable.stoppable(Arrays.asList(providers)).stop();
            } finally {
                Arrays.fill(providers, null);
            }
        }
    }

    /**
     * Allows using a {@link ServiceRegistry} as a provider for another {@link ServiceRegistry},
     * to create a parent-child relationship. This class is optimized for the case where the
     * delegate is a {@link DefaultServiceRegistry}, in which case it avoids unnecessary object
     * creation and exception handling.
     */
    private static class ParentServices implements Provider {
        private final ServiceRegistry parent;

        private ParentServices(ServiceRegistry parent) {
            this.parent = parent;
        }

        @Override
        public ServiceProvider getFactory(Class<?> serviceType) {
            if (parent instanceof DefaultServiceRegistry) {
                return ((DefaultServiceRegistry) parent).getFactoryProvider(serviceType);
            }
            try {
                Factory<?> factory = parent.getFactory(serviceType);
                return instanceToServiceProvider(factory);
            } catch (UnknownServiceException e) {
                if (!e.getType().equals(serviceType)) {
                    throw e;
                }
            }
            return null;
        }

        @Override
        public ServiceProvider getService(Type serviceType) {
            if (parent instanceof DefaultServiceRegistry) {
                return ((DefaultServiceRegistry) parent).getProvider(serviceType);
            }
            try {
                Object service = parent.get(serviceType);
                return instanceToServiceProvider(service);
            } catch (UnknownServiceException e) {
                if (!e.getType().equals(serviceType)) {
                    throw e;
                }
            }
            return null;
        }

        @Override
        public void getAll(Class<?> serviceType, List<ServiceProvider> result) {
            if (parent instanceof DefaultServiceRegistry) {
                ((DefaultServiceRegistry) parent).collectProviders(serviceType, result);
                return;
            }
            List<?> services = parent.getAll(serviceType);
            for (Object service : services) {
                result.add(instanceToServiceProvider(service));
            }
        }

        private ServiceProvider instanceToServiceProvider(final Object instance) {
            if (instance == null) {
                return null;
            }
            return new ServiceProvider() {
                public String getDisplayName() {
                    return "ServiceRegistry " + parent;
                }

                public Object get() {
                    return instance;
                }

                public void requiredBy(Provider provider) {
                    // Ignore
                }
            };
        }

        @Override
        public void stop() {
        }
    }

    public ServiceProvider find(Type serviceType, Provider provider) {
        if (serviceType instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) serviceType;
            Type rawType = parameterizedType.getRawType();
            if (rawType.equals(Factory.class)) {
                final Type typeArg = parameterizedType.getActualTypeArguments()[0];
                return getFactoryProvider(typeArg, provider);
            }
            if (rawType instanceof Class) {
                if (((Class<?>) rawType).isAssignableFrom(List.class)) {
                    Type typeArg = parameterizedType.getActualTypeArguments()[0];
                    return getCollectionProvider(typeArg, provider);
                }
                assertValidServiceType((Class<?>) rawType);
                return provider.getService(serviceType);
            }
        }
        if (serviceType instanceof Class<?>) {
            assertValidServiceType((Class<?>) serviceType);
            return provider.getService(serviceType);
        }

        throw new ServiceValidationException(String.format("Locating services with type %s is not supported.", format(serviceType)));
    }

    private ServiceProvider getFactoryProvider(Type type, Provider provider) {
        if (type instanceof Class) {
            return provider.getFactory((Class) type);
        }
        if (type instanceof WildcardType) {
            final WildcardType wildcardType = (WildcardType) type;
            if (wildcardType.getLowerBounds().length == 1 && wildcardType.getUpperBounds().length == 1) {
                if (wildcardType.getLowerBounds()[0] instanceof Class && wildcardType.getUpperBounds()[0].equals(Object.class)) {
                    return provider.getFactory((Class<?>) wildcardType.getLowerBounds()[0]);
                }
            }
            if (wildcardType.getLowerBounds().length == 0 && wildcardType.getUpperBounds().length == 1) {
                if (wildcardType.getUpperBounds()[0] instanceof Class) {
                    return provider.getFactory((Class<?>) wildcardType.getUpperBounds()[0]);
                }
            }
        }
        throw new ServiceValidationException(String.format("Locating services with type %s is not supported.", format(type)));
    }

    private ServiceProvider getCollectionProvider(Type elementType, Provider provider) {
        if (elementType instanceof Class) {
            Class<?> elementClass = (Class<?>) elementType;
            return getCollectionProvider(elementClass, provider);
        }
        if (elementType instanceof WildcardType) {
            WildcardType wildcardType = (WildcardType) elementType;
            if (wildcardType.getUpperBounds()[0] instanceof Class && wildcardType.getLowerBounds().length == 0) {
                Class<?> elementClass = (Class<?>) wildcardType.getUpperBounds()[0];
                return getCollectionProvider(elementClass, provider);
            }
        }
        throw new ServiceValidationException(String.format("Locating services with type %s is not supported.", format(elementType)));
    }

    private ServiceProvider getCollectionProvider(Class<?> elementClass, Provider provider) {
        assertValidServiceType(elementClass);
        List<ServiceProvider> providers = new ArrayList<ServiceProvider>();
        provider.getAll(elementClass, providers);
        List<Object> services = new ArrayList<Object>(providers.size());
        for (ServiceProvider serviceProvider : providers) {
            services.add(serviceProvider.get());
        }
        return new CollectionServiceProvider(elementClass, services, providers);
    }

    private static class CollectionServiceProvider implements ServiceProvider {
        private final Type typeArg;
        private final List<Object> services;
        private final List<ServiceProvider> providers;

        public CollectionServiceProvider(Type typeArg, List<Object> services, List<ServiceProvider> providers) {
            this.typeArg = typeArg;
            this.services = services;
            this.providers = providers;
        }

        @Override
        public String getDisplayName() {
            return "services with type " + typeArg;
        }

        @Override
        public Object get() {
            return services;
        }

        @Override
        public void requiredBy(Provider provider) {
            for (ServiceProvider serviceProvider : providers) {
                serviceProvider.requiredBy(provider);
            }
        }
    }

    private static boolean isSatisfiedBy(Type expected, Type actual) {
        if (expected == actual) {
            return true;
        }
        if (expected instanceof Class) {
            return isSatisfiedBy((Class<?>) expected, actual);
        }
        if (expected instanceof ParameterizedType) {
            return isSatisfiedBy((ParameterizedType) expected, actual);
        }
        return false;
    }

    private static boolean isSatisfiedBy(Class<?> expectedClass, Type actual) {
        if (actual instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) actual;
            if (parameterizedType.getRawType() instanceof Class) {
                return expectedClass.isAssignableFrom((Class) parameterizedType.getRawType());
            }
        } else if (actual instanceof Class) {
            Class<?> other = (Class<?>) actual;
            return expectedClass.isAssignableFrom(other);
        }
        return false;
    }

    private static boolean isSatisfiedBy(ParameterizedType expectedParameterizedType, Type actual) {
        Type expectedRawType = expectedParameterizedType.getRawType();
        if (actual instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) actual;
            if (!isSatisfiedBy(expectedRawType, parameterizedType.getRawType())) {
                return false;
            }
            Type[] expectedTypeArguments = expectedParameterizedType.getActualTypeArguments();
            for (int i = 0; i < parameterizedType.getActualTypeArguments().length; i++) {
                Type type = parameterizedType.getActualTypeArguments()[i];
                if (!isSatisfiedBy(expectedTypeArguments[i], type)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private static void assertValidServiceType(Class<?> serviceClass) {
        if (serviceClass.isArray()) {
            throw new ServiceValidationException("Locating services with array type is not supported.");
        }
        if (serviceClass.isAnnotation()) {
            throw new ServiceValidationException("Locating services with annotation type is not supported.");
        }
        if (serviceClass == Object.class) {
            throw new ServiceValidationException("Locating services with type Object is not supported.");
        }
    }
}
