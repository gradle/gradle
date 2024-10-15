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

import org.gradle.internal.Cast;
import org.gradle.internal.Factory;
import org.gradle.internal.InternalTransformer;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.concurrent.Stoppable;

import javax.annotation.Nullable;
import java.io.Closeable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Formatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Collections.singletonList;
import static org.gradle.util.internal.CollectionUtils.collect;
import static org.gradle.util.internal.CollectionUtils.join;

/**
 * A hierarchical {@link ServiceRegistry} implementation.
 *
 * <p>Subclasses can register services by:</p>
 *
 * <ul>
 *
 * <li>Calling {@link #add(Class, Object)} or {@link #add(Object)} to register a service instance.</li>
 *
 * <li>Calling {@link #addProvider(ServiceRegistrationProvider)} to register a service provider bean. A provider bean may have factory, decorator and configuration methods as described below.</li>
 *
 * <li>Adding a factory method. A factory method should be annotated with {@literal @}{@link Provides}, have a name that starts with 'create', and have a non-void return type.
 * For example, <code>@Provides protected SomeService createSomeService() { ....
 * }</code>.
 * Parameters are injected using services from this registry or its parents. Parameter of type {@link ServiceRegistry} will receive the service registry that owns the service. Parameter of
 * type {@code List<T>} will receive all services of type {@code T}, if any.
 * If a parameter has the same type as the return type of the factory method, then that parameter will be located in the parent registry.
 * This allows decorating services, i.e. specializing a service from a parent scope.</li>
 *
 * <li>Adding a configure method. A configure method should be called 'configure', take a {@link ServiceRegistration} parameter, and a have a void return type. Additional parameters are injected using
 * services from this registry or its parents.</li>
 *
 * </ul>
 *
 * <p>Service instances are created on demand. {@link #getFactory(Class)} looks for a service instance which implements {@code Factory<T>} where {@code T} is the expected type.</p>
 *
 * <p>Service instances and factories are closed when the registry that created them is closed using {@link #close()}. If a service instance or factory implements {@link java.io.Closeable} or {@link
 * org.gradle.internal.concurrent.Stoppable} then the appropriate {@link Closeable#close()} or {@link Stoppable#stop()} method is called. Instances are closed in reverse dependency order.</p>
 *
 * <p>Service registries are arranged in a hierarchy. If a service of a given type cannot be located, the registry uses its parent registry, if any, to locate the service.</p>
 *
 * <p>Service interfaces should be annotated with {@link org.gradle.internal.service.scopes.ServiceScope} to indicate their intended usage.</p>
 *
 * <p>Service interfaces can be annotated with {@link org.gradle.internal.service.scopes.StatefulListener} to indicate that services instances that implement the interface should
 * be registered as a listener of that type. Alternatively, service implementations can be annotated with {@link org.gradle.internal.service.scopes.ListenerService} to indicate that the should be
 * registered as a listener.</p>
 */
public class DefaultServiceRegistry implements CloseableServiceRegistry, ContainsServices, ServiceRegistrationProvider {
    private enum State {INIT, STARTED, CLOSED}

    private final static ServiceRegistry[] NO_PARENTS = new ServiceRegistry[0];
    private final static Service[] NO_DEPENDENTS = new Service[0];
    private final static Object[] NO_PARAMS = new Object[0];

    private final ClassInspector inspector;
    private final OwnServices ownServices;
    private final ServiceProvider allServices;
    private final ServiceProvider parentServices;
    @Nullable
    private final String displayName;
    private final ServiceProvider thisAsServiceProvider;

    private final AtomicReference<State> state = new AtomicReference<State>(State.INIT);

    public DefaultServiceRegistry() {
        this(null, NO_PARENTS);
    }

    public DefaultServiceRegistry(String displayName) {
        this(displayName, NO_PARENTS);
    }

    public DefaultServiceRegistry(ServiceRegistry... parents) {
        this(null, parents);
    }

    public DefaultServiceRegistry(@Nullable String displayName, ServiceRegistry... parents) {
        this.displayName = displayName;
        this.ownServices = new OwnServices();
        if (parents.length == 0) {
            this.parentServices = null;
            this.allServices = ownServices;
            this.inspector = new ClassInspector();
        } else {
            this.parentServices = setupParentServices(parents);
            this.allServices = new CompositeServiceProvider(ownServices, parentServices);
            this.inspector = parents[0] instanceof DefaultServiceRegistry ? ((DefaultServiceRegistry) parents[0]).inspector : new ClassInspector();
        }
        this.thisAsServiceProvider = allServices;

        ServiceAccessToken token = ServiceAccess.createToken(getDisplayName());
        findProviderMethods(this, token);
    }

    private static ServiceProvider setupParentServices(ServiceRegistry[] parents) {
        ServiceProvider parentServices;
        if (parents.length == 1) {
            parentServices = toParentServices(parents[0]);
        } else {
            ServiceProvider[] parentServiceProviders = new ServiceProvider[parents.length];
            for (int i = 0; i < parents.length; i++) {
                parentServiceProviders[i] = toParentServices(parents[i]);
            }
            parentServices = new CompositeServiceProvider(parentServiceProviders);
        }
        return parentServices;
    }

    @Override
    public ServiceProvider asProvider() {
        return thisAsServiceProvider;
    }

    private static ServiceProvider toParentServices(ServiceRegistry serviceRegistry) {
        if (serviceRegistry instanceof ContainsServices) {
            return new ParentServices(((ContainsServices) serviceRegistry).asProvider());
        }
        throw new IllegalArgumentException(String.format("Service registry %s cannot be used as a parent for another service registry.", serviceRegistry));
    }

    /**
     * Creates a service registry that uses the given providers.
     */
    public static ServiceRegistry create(ServiceRegistrationProvider... providers) {
        DefaultServiceRegistry registry = new DefaultServiceRegistry();
        for (ServiceRegistrationProvider provider : providers) {
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

    private void findProviderMethods(ServiceRegistrationProvider target, ServiceAccessToken token) {
        Class<? extends ServiceRegistrationProvider> type = target.getClass();
        RelevantMethods methods = RelevantMethods.getMethods(type);
        for (ServiceMethod method : methods.decorators) {
            if (parentServices == null) {
                throw new ServiceLookupException(String.format("Cannot use decorator method %s.%s() when no parent registry is provided.", type.getSimpleName(), method.getName()));
            }
            ownServices.add(new FactoryMethodService(this, determineAccessScope(method, token), token, target, method));
        }
        for (ServiceMethod method : methods.factories) {
            ownServices.add(new FactoryMethodService(this, determineAccessScope(method, token), token, target, method));
        }
        for (ServiceMethod method : methods.configurers) {
            applyConfigureMethod(token, method, target);
        }
    }

    private static ServiceAccessScope determineAccessScope(ServiceMethod method, ServiceAccessToken token) {
        PrivateService privateService = method.getMethod().getAnnotation(PrivateService.class);
        return privateService != null ? ServiceAccess.getPrivateScope(token) : ServiceAccess.getPublicScope();
    }

    private void applyConfigureMethod(ServiceAccessToken token, ServiceMethod method, Object target) {
        Object[] params = new Object[method.getParameterTypes().length];
        for (int i = 0; i < method.getParameterTypes().length; i++) {
            Type paramType = method.getParameterTypes()[i];
            if (paramType.equals(ServiceRegistration.class)) {
                params[i] = newRegistration(token);
            } else {
                Service paramProvider = find(paramType, token, allServices);
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
    public void register(ServiceRegistrationAction action) {
        assertMutable();
        ServiceAccessToken token = ServiceAccess.createToken(format(action.getClass()));
        action.registerServices(newRegistration(token));
    }

    private void assertMutable() {
        if (state.get() != State.INIT) {
            throw new IllegalStateException("Cannot add services to service registry " + this + " as it is no longer mutable");
        }
    }

    private ServiceRegistration newRegistration(final ServiceAccessToken token) {
        return new ServiceRegistration() {
            @Override
            public <T> void add(Class<T> serviceType, T serviceInstance) {
                DefaultServiceRegistry.this.add(ServiceAccess.getPublicScope(), serviceType, serviceInstance);
            }

            @Override
            public void add(Class<?> serviceType) {
                ownServices.add(new ConstructorService(DefaultServiceRegistry.this, ServiceAccess.getPublicScope(), token, serviceType));
            }

            @Override
            public <T> void add(Class<? super T> serviceType, Class<T> implementationType) {
                ownServices.add(new ConstructorService(DefaultServiceRegistry.this, ServiceAccess.getPublicScope(), token, serviceType, implementationType));
            }

            @Override
            public <T> void add(Class<? super T> serviceType1, Class<? super T> serviceType2, Class<T> implementationType) {
                //noinspection RedundantTypeArguments
                ownServices.add(new ConstructorService(DefaultServiceRegistry.this, ServiceAccess.getPublicScope(), token, Arrays.<Class<?>>asList(serviceType1, serviceType2), implementationType));
            }

            @Override
            public void addProvider(ServiceRegistrationProvider provider) {
                // The access token is intentionally not forwarded here
                DefaultServiceRegistry.this.addProvider(provider);
            }
        };
    }

    /**
     * Adds a service instance to this registry with the given public type. The given object is closed when this registry is closed.
     */
    public <T> DefaultServiceRegistry add(Class<? extends T> serviceType, T serviceInstance) {
        add(ServiceAccess.getPublicScope(), serviceType, serviceInstance);
        return this;
    }

    /**
     * Adds a service instance to this registry. The given object is closed when this registry is closed.
     */
    public DefaultServiceRegistry add(Object serviceInstance) {
        return add(serviceInstance.getClass(), serviceInstance);
    }

    private <T> void add(ServiceAccessScope accessScope, Class<? extends T> serviceType, T serviceInstance) {
        assertMutable();
        ownServices.add(new FixedInstanceService(this, accessScope, serviceType, serviceInstance));
    }

    /**
     * Adds a service provider bean to this registry. This provider may define factory and decorator methods.
     */
    public DefaultServiceRegistry addProvider(ServiceRegistrationProvider provider) {
        assertMutable();
        ServiceAccessToken token = org.gradle.internal.service.ServiceAccess.createToken(format(provider.getClass()));
        findProviderMethods(provider, token);
        return this;
    }

    /**
     * Closes all services for this registry. For each service, if the service has a public void close() or stop() method, that method is called to close the service.
     */
    @Override
    public void close() {
        noLongerMutable();
        if (state.compareAndSet(State.STARTED, State.CLOSED)) {
            CompositeStoppable.stoppable(allServices).stop();
        }
    }

    private void serviceRequested() {
        noLongerMutable();
        if (state.get() == State.CLOSED) {
            throw new IllegalStateException(String.format("%s has been closed.", getDisplayName()));
        }
    }

    private void noLongerMutable() {
        state.compareAndSet(State.INIT, State.STARTED);
    }

    public boolean isClosed() {
        return state.get() == State.CLOSED;
    }

    @Override
    public <T> T get(Class<T> serviceType) throws UnknownServiceException, ServiceLookupException {
        return serviceType.cast(get((Type) serviceType));
    }

    @Override
    public Object get(Type serviceType) throws UnknownServiceException, ServiceLookupException {
        Object instance = find(serviceType);
        if (instance == null) {
            throw new UnknownServiceException(serviceType, String.format("No service of type %s available in %s.", format(serviceType), getDisplayName()));
        }
        return instance;
    }

    @Override
    public Object get(Type serviceType, Class<? extends Annotation> annotatedWith) throws UnknownServiceException, ServiceLookupException {
        throw new UnknownServiceException(serviceType, String.format("No service of type %s annotated with @%s available in %s.", format(serviceType), annotatedWith.getSimpleName(), getDisplayName()));
    }

    @Override
    public Object find(Type serviceType) throws ServiceLookupException {
        assertValidServiceType(unwrap(serviceType));
        Service provider = getService(serviceType);
        return provider == null ? null : provider.get();
    }

    @Nullable
    private Service getService(Type serviceType) {
        serviceRequested();
        return find(serviceType, null, allServices);
    }

    @Override
    public <T> Factory<T> getFactory(Class<T> type) {
        assertValidServiceType(type);
        Service provider = getFactoryService(type);
        Factory<T> factory = provider == null ? null : Cast.<Factory<T>>uncheckedCast(provider.get());
        if (factory == null) {
            throw new UnknownServiceException(type, String.format("No factory for objects of type %s available in %s.", format(type), getDisplayName()));
        }
        return factory;
    }

    @Nullable
    private Service getFactoryService(Class<?> serviceType) {
        serviceRequested();
        return allServices.getFactory(serviceType, null);
    }

    @Override
    public <T> List<T> getAll(Class<T> serviceType) throws ServiceLookupException {
        assertValidServiceType(serviceType);
        List<T> services = new ArrayList<T>();
        serviceRequested();
        allServices.getAll(serviceType, null, new InstanceUnpackingVisitor<T>(serviceType, services));
        return services;
    }

    private static class InstanceUnpackingVisitor<T> implements ServiceProvider.Visitor {
        private final Class<T> serviceType;
        private final List<T> delegate;

        private InstanceUnpackingVisitor(Class<T> serviceType, List<T> delegate) {
            this.serviceType = serviceType;
            this.delegate = delegate;
        }

        @Override
        public void visit(Service service) {
            T instance = serviceType.cast(service.get());
            if (!delegate.contains(instance)) {
                delegate.add(instance);
            }
        }
    }

    private static class CollectingVisitor implements ServiceProvider.Visitor {
        private final List<Service> delegate;

        private CollectingVisitor(List<Service> delegate) {
            this.delegate = delegate;
        }

        @Override
        public void visit(Service service) {
            if (!delegate.contains(service)) {
                delegate.add(service);
            }
        }
    }

    @Override
    public <T> T newInstance(Class<T> type) {
        return getFactory(type).create();
    }

    private class OwnServices implements ServiceProvider {
        private final Map<Class<?>, List<ServiceProvider>> providersByType = new HashMap<Class<?>, List<ServiceProvider>>(16, 0.5f);
        private final CompositeStoppable stoppable = CompositeStoppable.stoppable();
        private final List<SingletonService> services = new ArrayList<SingletonService>();
        private final List<AnnotatedServiceLifecycleHandler> lifecycleHandlers = new ArrayList<AnnotatedServiceLifecycleHandler>();

        public OwnServices() {
            providersByType.put(ServiceRegistry.class, Collections.<ServiceProvider>singletonList(new ThisAsService(ServiceAccess.getPublicScope())));
        }

        @Override
        public Service getFactory(Class<?> type, @Nullable ServiceAccessToken token) {
            List<ServiceProvider> serviceProviders = getProviders(Factory.class);
            if (serviceProviders.isEmpty()) {
                return null;
            }
            if (serviceProviders.size() == 1) {
                return serviceProviders.get(0).getFactory(type, token);
            }

            List<Service> services = new ArrayList<Service>(serviceProviders.size());
            for (ServiceProvider serviceProvider : serviceProviders) {
                Service service = serviceProvider.getFactory(type, token);
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
            for (Service candidate : services) {
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
        public Service getService(Type type, @Nullable ServiceAccessToken token) {
            List<ServiceProvider> serviceProviders = getProviders(unwrap(type));
            if (serviceProviders.isEmpty()) {
                return null;
            }
            if (serviceProviders.size() == 1) {
                return serviceProviders.get(0).getService(type, token);
            }

            List<Service> services = new ArrayList<Service>(serviceProviders.size());
            for (ServiceProvider serviceProvider : serviceProviders) {
                Service service = serviceProvider.getService(type, token);
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
            for (Service candidate : services) {
                descriptions.add(candidate.getDisplayName());
            }

            Formatter formatter = new Formatter();
            formatter.format("Multiple services of type %s available in %s:", format(type), getDisplayName());
            for (String description : descriptions) {
                formatter.format("%n   - %s", description);
            }
            throw new ServiceLookupException(formatter.toString());
        }

        private List<ServiceProvider> getProviders(Class<?> type) {
            List<ServiceProvider> providers = providersByType.get(type);
            return providers == null ? Collections.<ServiceProvider>emptyList() : providers;
        }

        @Override
        public Visitor getAll(Class<?> serviceType, @Nullable ServiceAccessToken token, Visitor visitor) {
            for (ServiceProvider serviceProvider : getProviders(serviceType)) {
                visitor = serviceProvider.getAll(serviceType, token, visitor);
            }
            return visitor;
        }

        @Override
        public void stop() {
            stoppable.stop();
        }

        public void add(SingletonService serviceProvider) {
            assertMutable();
            stoppable.add(serviceProvider);
            collectProvidersForClassHierarchy(inspector, serviceProvider.getDeclaredServiceTypes(), serviceProvider);
            services.add(serviceProvider);
            for (AnnotatedServiceLifecycleHandler annotationHandler : lifecycleHandlers) {
                notifyAnnotationHandler(annotationHandler, serviceProvider);
            }
        }

        public void collectProvidersForClassHierarchy(ClassInspector inspector, List<Class<?>> declaredServiceTypes, ServiceProvider serviceProvider) {
            for (Class<?> serviceType : declaredServiceTypes) {
                for (Class<?> type : inspector.getHierarchy(serviceType)) {
                    if (type.equals(Object.class)) {
                        continue;
                    }
                    if (type.equals(ServiceRegistry.class)) {
                        // Disallow custom services of type ServiceRegistry, as these are automatically provided
                        throw new IllegalArgumentException("Cannot define a service of type ServiceRegistry: " + serviceProvider);
                    }
                    putServiceType(type, serviceProvider);
                }
            }
        }

        private void putServiceType(Class<?> type, ServiceProvider serviceProvider) {
            List<ServiceProvider> serviceProviders = providersByType.get(type);
            if (serviceProviders == null) {
                serviceProviders = new ArrayList<ServiceProvider>(2);
                serviceProviders.add(serviceProvider);
                providersByType.put(type, serviceProviders);
                return;
            }

            // Adding of the service provider for the same type may happen when it has multiple declared service types
            if (!serviceProviders.contains(serviceProvider)) {
                serviceProviders.add(serviceProvider);
            }
        }

        public void instanceRealized(ManagedObjectServiceProvider serviceProvider, Object instance) {
            List<Class<?>> declaredServiceTypes = serviceProvider.getDeclaredServiceTypes();
            if (instance instanceof AnnotatedServiceLifecycleHandler && !isAssignableFromAnyType(AnnotatedServiceLifecycleHandler.class, serviceProvider.getDeclaredServiceTypes())) {
                throw new IllegalStateException(String.format("%s implements %s but is not declared as a service of this type. This service is declared as having %s.",
                    serviceProvider.getDisplayName(), AnnotatedServiceLifecycleHandler.class.getSimpleName(), format("type", declaredServiceTypes)));
            }
            if (instance instanceof AnnotatedServiceLifecycleHandler) {
                annotationHandlerCreated((AnnotatedServiceLifecycleHandler) instance);
            }
            for (AnnotatedServiceLifecycleHandler lifecycleHandler : lifecycleHandlers) {
                for (Class<? extends Annotation> annotation : lifecycleHandler.getAnnotations()) {
                    boolean implementationHasAnnotation = inspector.hasAnnotation(instance.getClass(), annotation);
                    boolean declaredWithAnnotation = anyTypeHasAnnotation(annotation, declaredServiceTypes);
                    if (implementationHasAnnotation && !declaredWithAnnotation) {
                        throw new IllegalStateException(String.format("%s is annotated with @%s but is not declared as a service with this annotation. This service is declared as having %s.",
                            serviceProvider.getDisplayName(), format(annotation), format("type", declaredServiceTypes)));
                    }
                }
            }
        }

        void annotationHandlerCreated(AnnotatedServiceLifecycleHandler annotationHandler) {
            lifecycleHandlers.add(annotationHandler);
            for (SingletonService candidate : services) {
                notifyAnnotationHandler(annotationHandler, candidate);
            }
        }

        private void notifyAnnotationHandler(AnnotatedServiceLifecycleHandler annotationHandler, SingletonService candidate) {
            if (annotationHandler.getImplicitAnnotation() != null) {
                annotationHandler.whenRegistered(annotationHandler.getImplicitAnnotation(), new RegistrationWrapper(candidate));
            } else {
                List<Class<?>> declaredServiceTypes = candidate.getDeclaredServiceTypes();
                for (Class<? extends Annotation> annotation : annotationHandler.getAnnotations()) {
                    if (anyTypeHasAnnotation(annotation, declaredServiceTypes)) {
                        annotationHandler.whenRegistered(annotation, new RegistrationWrapper(candidate));
                    }
                }
            }
        }

        private boolean anyTypeHasAnnotation(Class<? extends Annotation> annotation, List<Class<?>> types) {
            for (Class<?> type : types) {
                if (inspector.hasAnnotation(type, annotation)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public String toString() {
            return getDisplayName();
        }
    }

    private class RegistrationWrapper implements AnnotatedServiceLifecycleHandler.Registration {
        private final SingletonService serviceProvider;

        public RegistrationWrapper(SingletonService serviceProvider) {
            this.serviceProvider = serviceProvider;
        }

        @Override
        public List<Class<?>> getDeclaredTypes() {
            return serviceProvider.getDeclaredServiceTypes();
        }

        @Override
        public Object getInstance() {
            serviceRequested();
            return serviceProvider.getPreparedInstance();
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

    private static abstract class ManagedObjectServiceProvider implements ServiceProvider, Service {
        protected final DefaultServiceRegistry owner;
        private final Queue<ServiceProvider> dependents = new ConcurrentLinkedQueue<ServiceProvider>();
        private volatile Object instance;

        protected ManagedObjectServiceProvider(DefaultServiceRegistry owner) {
            this.owner = owner;
        }

        abstract List<Class<?>> getDeclaredServiceTypes();

        protected void setInstance(Object instance) {
            this.instance = instance;
            owner.ownServices.instanceRealized(this, instance);
        }

        public final Object getInstance() {
            Object result = instance;
            if (result == null) {
                synchronized (this) {
                    result = instance;
                    if (result == null) {
                        setInstance(createServiceInstance());
                        result = instance;
                    }
                }
            }
            return result;
        }

        /**
         * Subclasses implement this method to create the service instance. It is never called concurrently and may not return null.
         */
        protected abstract Object createServiceInstance();

        @Override
        public final void requiredBy(ServiceProvider serviceProvider) {
            if (fromSameRegistry(serviceProvider)) {
                dependents.add(serviceProvider);
            }
        }

        private boolean fromSameRegistry(ServiceProvider serviceProvider) {
            return serviceProvider instanceof ManagedObjectServiceProvider && ((ManagedObjectServiceProvider) serviceProvider).owner == owner;
        }

        @Override
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

    private static abstract class SingletonService extends ManagedObjectServiceProvider {
        private enum BindState {UNBOUND, BINDING, BOUND}

        protected final ServiceAccessScope accessScope;
        protected final List<? extends Type> serviceTypes;
        private final List<Class<?>> serviceTypesAsClasses;

        BindState state = BindState.UNBOUND;

        // Singleton service is implemented by a single instance and must extend/implement all declared service types.
        // But it can only implement a single `Factory<? extends ElementType>` due to Java type constraints.
        // The value of the field is computed lazily.
        Class<?> factoryElementType;

        SingletonService(DefaultServiceRegistry owner, ServiceAccessScope accessScope, List<? extends Type> serviceTypes) {
            super(owner);

            if (serviceTypes.isEmpty()) {
                throw new IllegalArgumentException("Expected at least one declared service type");
            }

            this.accessScope = accessScope;
            this.serviceTypes = serviceTypes;
            serviceTypesAsClasses = collect(serviceTypes, new InternalTransformer<Class<?>, Type>() {
                @Override
                public Class<?> transform(Type type) {
                    return unwrap(type);
                }
            });
        }

        @Override
        public List<Class<?>> getDeclaredServiceTypes() {
            return serviceTypesAsClasses;
        }

        @Override
        public String getDisplayName() {
            return format("Service", serviceTypes);
        }

        @Override
        public String toString() {
            return getDisplayName();
        }

        @Override
        public Object get() {
            return getInstance();
        }

        private Object getPreparedInstance() {
            return prepare().get();
        }

        private Service prepare() {
            if (state == BindState.BOUND) {
                return this;
            }
            synchronized (this) {
                if (state == BindState.BINDING) {
                    throw new ServiceValidationException("Cycle in dependencies of " + getDisplayName() + " detected");
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
         * Do any preparation work and validation to ensure that {@link #createServiceInstance()} can be called later.
         * This method is never called concurrently.
         */
        protected void bind() {
        }

        @Override
        public Service getService(Type serviceType, @Nullable ServiceAccessToken token) {
            if (!accessScope.contains(token)) {
                return null;
            }
            if (!isSatisfiedByAny(serviceType, serviceTypes)) {
                return null;
            }
            return prepare();
        }

        @Override
        public Visitor getAll(Class<?> serviceType, @Nullable ServiceAccessToken token, Visitor visitor) {
            if (!accessScope.contains(token)) {
                return visitor;
            }
            if (isAssignableFromAnyType(serviceType, serviceTypesAsClasses)) {
                visitor.visit(prepare());
            }
            return visitor;
        }

        @Override
        public Service getFactory(Class<?> elementType, @Nullable ServiceAccessToken token) {
            if (!accessScope.contains(token)) {
                return null;
            }
            if (!isFactoryFor(elementType)) {
                return null;
            }
            return prepare();
        }

        // Finds the first element type of `Factory<? extends ElementType>` in the type hierarchy
        @Nullable
        private static Class<?> findFactoryElementType(Type type) {
            Class<?> c = unwrap(type);
            if (!Factory.class.isAssignableFrom(c)) {
                return null;
            }

            if (type instanceof ParameterizedType) {
                // Check if type is Factory<? extends ElementType>
                ParameterizedType parameterizedType = (ParameterizedType) type;
                if (parameterizedType.getRawType().equals(Factory.class)) {
                    Type actualType = parameterizedType.getActualTypeArguments()[0];
                    if (actualType instanceof Class) {
                        return (Class<?>) actualType;
                    }
                }
            }

            // Check if type extends Factory<? extends ElementType>
            for (Type interfaceType : c.getGenericInterfaces()) {
                Class<?> parentFactoryElementType = findFactoryElementType(interfaceType);
                if (parentFactoryElementType != null) {
                    return parentFactoryElementType;
                }
            }

            return null;
        }

        @Nullable
        private static Class<?> findFactoryElementType(List<? extends Type> factoryCandidates) {
            for (Type factoryCandidate : factoryCandidates) {
                Class<?> factoryElementType = findFactoryElementType(factoryCandidate);
                if (factoryElementType != null) {
                    return factoryElementType;
                }
            }

            return null;
        }

        private boolean isFactoryFor(Class<?> elementType) {
            // This method can be called concurrently, but in the worst case we repeat the computation
            if (factoryElementType == null) {
                Class<?> foundFactoryElementType = findFactoryElementType(serviceTypes);
                factoryElementType = foundFactoryElementType == null ? NonFactoryMarker.class : foundFactoryElementType;
            }

            return !factoryElementType.equals(NonFactoryMarker.class) && elementType.isAssignableFrom(factoryElementType);
        }

        private interface NonFactoryMarker {}
    }

    private static abstract class FactoryService extends SingletonService {
        private final ServiceAccessToken accessToken;
        private Service[] paramServices;
        private Service decorates;

        protected FactoryService(DefaultServiceRegistry owner, ServiceAccessScope accessScope, ServiceAccessToken accessToken, List<? extends Type> serviceTypes) {
            super(owner, accessScope, serviceTypes);
            this.accessToken = accessToken;
        }

        protected abstract Type[] getParameterTypes();

        protected abstract String getFactoryDisplayName();

        @Override
        protected void bind() {
            Type[] parameterTypes = getParameterTypes();
            if (parameterTypes.length == 0) {
                paramServices = NO_DEPENDENTS;
                return;
            }
            paramServices = new Service[parameterTypes.length];
            for (int i = 0; i < parameterTypes.length; i++) {
                Type paramType = parameterTypes[i];
                if (isEqualToAnyType(paramType, serviceTypes)) {
                    // A decorating factory
                    Service paramProvider = find(paramType, accessToken, owner.parentServices);
                    if (paramProvider == null) {
                        throw new ServiceCreationException(String.format("Cannot create service of %s using %s as required service of type %s for parameter #%s is not available in parent registries.",
                            format("type", serviceTypes),
                            getFactoryDisplayName(),
                            format(paramType),
                            i + 1));
                    }
                    paramServices[i] = paramProvider;
                    decorates = paramProvider;
                } else {
                    Service paramProvider;
                    try {
                        paramProvider = find(paramType, accessToken, owner.allServices);
                    } catch (ServiceLookupException e) {
                        throw new ServiceCreationException(String.format("Cannot create service of %s using %s as there is a problem with parameter #%s of type %s.",
                            format("type", serviceTypes),
                            getFactoryDisplayName(),
                            i + 1,
                            format(paramType)), e);
                    }
                    if (paramProvider == null) {
                        throw new ServiceCreationException(String.format("Cannot create service of %s using %s as required service of type %s for parameter #%s is not available.",
                            format("type", serviceTypes),
                            getFactoryDisplayName(),
                            format(paramType),
                            i + 1));

                    }
                    paramServices[i] = paramProvider;
                    paramProvider.requiredBy(this);
                }
            }
        }

        @Override
        protected Object createServiceInstance() {
            Object[] params = assembleParameters();
            Object result = invokeMethod(params);
            // Can discard the state required to create instance
            paramServices = null;
            return result;
        }

        private Object[] assembleParameters() {
            if (paramServices == NO_DEPENDENTS) {
                return NO_PARAMS;
            }
            Object[] params = new Object[paramServices.length];
            for (int i = 0; i < paramServices.length; i++) {
                Service paramProvider = paramServices[i];
                params[i] = paramProvider.get();
            }
            return params;
        }

        @Override
        public Visitor getAll(Class<?> serviceType, @Nullable ServiceAccessToken token, final Visitor visitor) {
            super.getAll(serviceType, token, visitor);
            if (decorates == null) {
                return visitor;
            } else {
                return new Visitor() {
                    @Override
                    public void visit(Service service) {
                        // Ignore the decorated service
                        if (service != decorates) {
                            visitor.visit(service);
                        }
                    }
                };
            }
        }

        protected abstract Object invokeMethod(Object[] params);
    }

    private static class FactoryMethodService extends FactoryService {

        @Nullable
        private ServiceMethod method;
        @Nullable
        private Object target;

        public FactoryMethodService(DefaultServiceRegistry owner, ServiceAccessScope accessScope, ServiceAccessToken token, Object target, ServiceMethod method) {
            this(owner, accessScope, token, serviceTypesOf(method), target, method);
        }

        private FactoryMethodService(DefaultServiceRegistry owner, ServiceAccessScope accessScope, ServiceAccessToken token, List<? extends Type> serviceTypes, Object target, ServiceMethod method) {
            super(owner, accessScope, token, serviceTypes);
            validateImplementationForServiceTypes(serviceTypes, method.getServiceType());
            this.target = target;
            this.method = method;
        }

        private static List<? extends Type> serviceTypesOf(ServiceMethod method) {
            Class<?>[] explicitServiceTypes = method.getMethod().getAnnotation(Provides.class).value();
            return explicitServiceTypes.length == 0 ? singletonList(method.getServiceType()) : Arrays.asList(explicitServiceTypes);
        }

        @Override
        public String getDisplayName() {
            if (method == null) {
                return super.getDisplayName();
            }

            return format("Service", serviceTypes) + " via " + format(method.getOwner()) + "." + method.getName() + "()";
        }

        @Override
        protected Type[] getParameterTypes() {
            return getMethod().getParameterTypes();
        }

        private ServiceMethod getMethod() {
            ServiceMethod method = this.method;
            if (method == null) {
                throw new IllegalStateException("Method is no longer available for the instance of " + format("service", serviceTypes));
            }
            return method;
        }

        @Override
        protected String getFactoryDisplayName() {
            return String.format("method %s.%s()", format(getMethod().getOwner()), getMethod().getName());
        }

        @Override
        protected Object invokeMethod(Object[] params) {
            if (target == null) {
                throw new IllegalStateException("The target of the factory method has been discarded after the first service creation attempt");
            }

            Object result;
            ServiceMethod method = getMethod();
            try {
                result = method.invoke(target, params);
            } catch (Exception e) {
                throw new ServiceCreationException(String.format("Could not create service of %s using %s.%s().",
                    format("type", serviceTypes),
                    method.getOwner().getSimpleName(),
                    method.getName()),
                    e);
            }

            if (result == null) {
                throw new ServiceCreationException(String.format("Could not create service of %s using %s.%s() as this method returned null.",
                    format("type", serviceTypes),
                    method.getOwner().getSimpleName(),
                    method.getName()));
            }
            return result;
        }

        @Override
        protected Object createServiceInstance() {
            Object result = super.createServiceInstance();
            this.target = null;
            this.method = null;
            return result;
        }
    }

    private static class FixedInstanceService extends SingletonService {
        public FixedInstanceService(DefaultServiceRegistry owner, ServiceAccessScope accessScope, Class<?> serviceType, Object serviceInstance) {
            super(owner, accessScope, singletonList(serviceType));
            setInstance(serviceInstance);
        }

        @Override
        public String getDisplayName() {
            return format("Service", serviceTypes) + " with implementation " + format(getInstance().getClass());
        }

        @Override
        protected Object createServiceInstance() {
            throw new UnsupportedOperationException();
        }
    }

    private static class ConstructorService extends FactoryService {
        @Nullable
        private Constructor<?> constructor;

        private ConstructorService(DefaultServiceRegistry owner, ServiceAccessScope accessScope, ServiceAccessToken token, Class<?> serviceType) {
            this(owner, accessScope, token, serviceType, serviceType);
        }

        private ConstructorService(DefaultServiceRegistry owner, ServiceAccessScope accessScope, ServiceAccessToken token, Class<?> serviceType, Class<?> implementationType) {
            this(owner, accessScope, token, Collections.<Class<?>>singletonList(serviceType), implementationType);
        }

        private ConstructorService(DefaultServiceRegistry owner, ServiceAccessScope accessScope, ServiceAccessToken token, List<? extends Type> serviceTypes, Class<?> implementationType) {
            super(owner, accessScope, token, serviceTypes);

            if (implementationType.isInterface()) {
                throw new ServiceValidationException("Cannot register an interface for construction.");
            }

            validateImplementationForServiceTypes(serviceTypes, implementationType);

            Constructor<?> match = InjectUtil.selectConstructor(implementationType);
            if (InjectUtil.isPackagePrivate(match.getModifiers()) || Modifier.isPrivate(match.getModifiers())) {
                match.setAccessible(true);
            }
            this.constructor = match;
        }

        @Override
        protected Type[] getParameterTypes() {
            return getConstructor().getGenericParameterTypes();
        }

        @Override
        protected Object createServiceInstance() {
            Object result = super.createServiceInstance();
            this.constructor = null;
            return result;
        }

        @Override
        public String getDisplayName() {
            if (constructor == null) {
                return super.getDisplayName();
            }

            return format("Service", serviceTypes) + " via " + format(getConstructor().getDeclaringClass()) + " constructor";
        }

        @Override
        protected String getFactoryDisplayName() {
            return String.format("%s constructor", format(getConstructor().getDeclaringClass()));
        }

        @Override
        protected Object invokeMethod(Object[] params) {
            try {
                return getConstructor().newInstance(params);
            } catch (InvocationTargetException e) {
                throw new ServiceCreationException(String.format("Could not create service of %s.", format("type", serviceTypes)), e.getCause());
            } catch (Exception e) {
                throw new ServiceCreationException(String.format("Could not create service of %s.", format("type", serviceTypes)), e);
            }
        }

        private Constructor<?> getConstructor() {
            Constructor<?> constructor = this.constructor;
            if (constructor == null) {
                throw new IllegalStateException("Constructor is no longer available for the instance of " + format("service", serviceTypes));
            }
            return constructor;
        }
    }

    private static void validateImplementationForServiceTypes(List<? extends Type> serviceTypes, Type implementationType) {
        Class<?> implementationClass = unwrap(implementationType);
        for (Type serviceType : serviceTypes) {
            Class<?> serviceClass = unwrap(serviceType);
            if (!serviceClass.isAssignableFrom(implementationClass)) {
                throw new ServiceValidationException(String.format("Cannot register implementation '%s' for service '%s', because it does not implement it",
                    implementationClass.getSimpleName(), serviceClass.getSimpleName()));
            }
        }
    }

    private static class CompositeServiceProvider implements ServiceProvider {
        private final ServiceProvider[] serviceProviders;

        private CompositeServiceProvider(ServiceProvider... serviceProviders) {
            this.serviceProviders = serviceProviders;
        }

        @Override
        public Service getService(Type serviceType, @Nullable ServiceAccessToken token) {
            for (ServiceProvider serviceProvider : serviceProviders) {
                Service service = serviceProvider.getService(serviceType, token);
                if (service != null) {
                    return service;
                }
            }
            return null;
        }

        @Override
        public Service getFactory(Class<?> type, @Nullable ServiceAccessToken token) {
            for (ServiceProvider serviceProvider : serviceProviders) {
                Service factory = serviceProvider.getFactory(type, token);
                if (factory != null) {
                    return factory;
                }
            }
            return null;
        }

        @Override
        public Visitor getAll(Class<?> serviceType, @Nullable ServiceAccessToken token, Visitor visitor) {
            for (ServiceProvider serviceProvider : serviceProviders) {
                visitor = serviceProvider.getAll(serviceType, token, visitor);
            }
            return visitor;
        }

        @Override
        public void stop() {
            try {
                CompositeStoppable.stoppable(Arrays.asList(serviceProviders)).stop();
            } finally {
                Arrays.fill(serviceProviders, null);
            }
        }

        @Override
        public String toString() {
            return Arrays.toString(serviceProviders);
        }
    }

    /**
     * Wraps a parent to ignore stop requests.
     */
    private static class ParentServices implements ServiceProvider {
        private final ServiceProvider parent;

        private ParentServices(ServiceProvider parent) {
            this.parent = parent;
        }

        @Override
        public Service getFactory(Class<?> serviceType, @Nullable ServiceAccessToken token) {
            return parent.getFactory(serviceType, token);
        }

        @Override
        public Service getService(Type serviceType, @Nullable ServiceAccessToken token) {
            return parent.getService(serviceType, token);
        }

        @Override
        public Visitor getAll(Class<?> serviceType, @Nullable ServiceAccessToken token, Visitor visitor) {
            return parent.getAll(serviceType, token, visitor);
        }

        @Override
        public void stop() {
        }

        @Override
        public String toString() {
            return parent.toString();
        }
    }

    @Nullable
    private static Service find(Type serviceType, @Nullable ServiceAccessToken token, ServiceProvider serviceProvider) {
        if (serviceType instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) serviceType;
            Type rawType = parameterizedType.getRawType();
            if (rawType.equals(Factory.class)) {
                final Type typeArg = parameterizedType.getActualTypeArguments()[0];
                return getFactoryService(typeArg, token, serviceProvider);
            }
            if (rawType instanceof Class) {
                if (((Class<?>) rawType).isAssignableFrom(List.class)) {
                    Type typeArg = parameterizedType.getActualTypeArguments()[0];
                    return getCollectionService(typeArg, token, serviceProvider);
                }
                assertValidServiceType((Class<?>) rawType);
                return serviceProvider.getService(serviceType, token);
            }
        }
        if (serviceType instanceof Class<?>) {
            assertValidServiceType((Class<?>) serviceType);
            return serviceProvider.getService(serviceType, token);
        }

        throw new ServiceValidationException(String.format("Locating services with type %s is not supported.", format(serviceType)));
    }

    @Nullable
    private static Service getFactoryService(Type type, @Nullable ServiceAccessToken token, ServiceProvider serviceProvider) {
        if (type instanceof Class) {
            return serviceProvider.getFactory((Class) type, token);
        }
        if (type instanceof WildcardType) {
            final WildcardType wildcardType = (WildcardType) type;
            if (wildcardType.getLowerBounds().length == 1 && wildcardType.getUpperBounds().length == 1) {
                if (wildcardType.getLowerBounds()[0] instanceof Class && wildcardType.getUpperBounds()[0].equals(Object.class)) {
                    return serviceProvider.getFactory((Class<?>) wildcardType.getLowerBounds()[0], token);
                }
            }
            if (wildcardType.getLowerBounds().length == 0 && wildcardType.getUpperBounds().length == 1) {
                if (wildcardType.getUpperBounds()[0] instanceof Class) {
                    return serviceProvider.getFactory((Class<?>) wildcardType.getUpperBounds()[0], token);
                }
            }
        }
        throw new ServiceValidationException(String.format("Locating services with type %s is not supported.", format(type)));
    }

    private static Service getCollectionService(Type elementType, @Nullable ServiceAccessToken token, ServiceProvider serviceProvider) {
        if (elementType instanceof Class) {
            Class<?> elementClass = (Class<?>) elementType;
            return getCollectionService(elementClass, token, serviceProvider);
        }
        if (elementType instanceof WildcardType) {
            WildcardType wildcardType = (WildcardType) elementType;
            if (wildcardType.getUpperBounds()[0] instanceof Class && wildcardType.getLowerBounds().length == 0) {
                Class<?> elementClass = (Class<?>) wildcardType.getUpperBounds()[0];
                return getCollectionService(elementClass, token, serviceProvider);
            }
        }
        throw new ServiceValidationException(String.format("Locating services with type %s is not supported.", format(elementType)));
    }

    private static Service getCollectionService(Class<?> elementClass, @Nullable ServiceAccessToken token, ServiceProvider serviceProvider) {
        assertValidServiceType(elementClass);
        List<Service> providers = new ArrayList<Service>();
        serviceProvider.getAll(elementClass, token, new CollectingVisitor(providers));
        List<Object> services = new ArrayList<Object>(providers.size());
        for (Service service : providers) {
            services.add(service.get());
        }
        return new CollectionService(elementClass, services, providers);
    }

    private static class CollectionService implements Service {
        private final Type typeArg;
        private final List<Object> services;
        private final List<Service> providers;

        public CollectionService(Type typeArg, List<Object> services, List<Service> providers) {
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
        public void requiredBy(ServiceProvider serviceProvider) {
            for (Service service : providers) {
                service.requiredBy(serviceProvider);
            }
        }
    }

    private static boolean isAssignableFromAnyType(Class<?> targetType, List<Class<?>> candidateTypes) {
        for (Class<?> candidate : candidateTypes) {
            if (targetType.isAssignableFrom(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isEqualToAnyType(Type targetType, List<? extends Type> candidateTypes) {
        for (Type candidate : candidateTypes) {
            if (targetType.equals(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSatisfiedByAny(Type expected, List<? extends Type> candidates) {
        for (Type candidate : candidates) {
            if (isSatisfiedBy(expected, candidate)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSatisfiedBy(Type expected, Type actual) {
        if (expected.equals(actual)) {
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

    private static String format(Type type) {
        return TypeStringFormatter.format(type);
    }

    private static String format(String qualifier, List<? extends Type> types) {
        if (types.size() == 1) {
            return qualifier + " " + format(types);
        } else {
            return qualifier + "s " + format(types);
        }
    }

    private static String format(List<? extends Type> types) {
        if (types.size() == 1) {
            return TypeStringFormatter.format(types.get(0));
        } else {
            return join(", ", types, new InternalTransformer<String, Type>() {
                @Override
                public String transform(Type type) {
                    return TypeStringFormatter.format(type);
                }
            });
        }
    }

    private class ThisAsService implements ServiceProvider, Service {

        private final ServiceAccessScope accessScope;

        private ThisAsService(ServiceAccessScope accessScope) {
            this.accessScope = accessScope;
        }

        @Override
        public Service getService(Type serviceType, @Nullable ServiceAccessToken token) {
            if (!accessScope.contains(token)) {
                return null;
            }
            if (serviceType.equals(ServiceRegistry.class)) {
                return this;
            }
            return null;
        }

        @Override
        public Service getFactory(Class<?> type, @Nullable ServiceAccessToken token) {
            // Note: if any implementation is added, it must check the [accessScope] first
            return null;
        }

        @Override
        public Visitor getAll(Class<?> serviceType, @Nullable ServiceAccessToken token, Visitor visitor) {
            if (!accessScope.contains(token)) {
                return visitor;
            }
            if (serviceType.equals(ServiceRegistry.class)) {
                visitor.visit(this);
            }
            return visitor;
        }

        @Override
        public void stop() {
        }

        @Override
        public String getDisplayName() {
            return "ServiceRegistry " + DefaultServiceRegistry.this.getDisplayName();
        }

        @Override
        public Object get() {
            return DefaultServiceRegistry.this;
        }

        @Override
        public void requiredBy(ServiceProvider serviceProvider) {
        }
    }

    private static class ClassInspector {
        private final ConcurrentMap<Class<?>, ClassDetails> classes = new ConcurrentHashMap<Class<?>, ClassDetails>();

        /**
         * Does the given class have the given annotation somewhere in its hierarchy?
         */
        boolean hasAnnotation(Class<?> type, Class<? extends Annotation> annotationType) {
            return getDetailsForClass(type).hasAnnotation(annotationType);
        }

        Set<Class<?>> getHierarchy(Class<?> type) {
            return getDetailsForClass(type).types;
        }

        private ClassDetails getDetailsForClass(Class<?> type) {
            ClassDetails classDetails = classes.get(type);
            if (classDetails == null) {
                // Multiple thread may calculate this at the same time, which is ok. All threads should end up with the same details object
                ClassDetails newDetails = new ClassDetails(type);
                classDetails = classes.putIfAbsent(type, newDetails);
                if (classDetails == null) {
                    classDetails = newDetails;
                }
            }
            return classDetails;
        }

        private static class ClassDetails {
            private final Set<Class<?>> types = new HashSet<Class<?>>();
            private final ConcurrentMap<Class<? extends Annotation>, Boolean> annotations = new ConcurrentHashMap<Class<? extends Annotation>, Boolean>();

            public ClassDetails(Class<?> type) {
                collectTypes(type, types);
            }

            private void collectTypes(@Nullable Class<?> type, Set<Class<?>> types) {
                if (type == null || !types.add(type)) {
                    return;
                }
                collectTypes(type.getSuperclass(), types);
                for (Class<?> serviceInterface : type.getInterfaces()) {
                    collectTypes(serviceInterface, types);
                }
            }

            public boolean hasAnnotation(Class<? extends Annotation> annotationType) {
                Boolean present = annotations.get(annotationType);
                if (present == null) {
                    // Multiple threads may calculate this at the same time, which is ok
                    present = locateAnnotation(annotationType);
                    annotations.putIfAbsent(annotationType, present);
                }
                return present;
            }

            private boolean locateAnnotation(Class<? extends Annotation> annotation) {
                for (Class<?> type : types) {
                    if (type.getAnnotation(annotation) != null) {
                        return true;
                    }
                }
                return false;
            }
        }
    }
}
