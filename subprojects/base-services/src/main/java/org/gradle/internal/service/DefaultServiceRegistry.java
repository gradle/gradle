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

import java.io.Closeable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

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
 * type {@code List<T>} will receive all services of type T, if any. If a parameter has the same type as the return type of the factory method, then that parameter will be looked up in the parent registry.
 * This allows decorating services.</li>
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
public class DefaultServiceRegistry implements ServiceRegistry, Closeable, ContainsServices {
    private enum State {INIT, STARTED, CLOSED};
    private final static ServiceRegistry[] NO_PARENTS = new ServiceRegistry[0];
    private final static Service[] NO_DEPENDENTS = new Service[0];
    private final static Object[] NO_PARAMS = new Object[0];

    private final OwnServices ownServices;
    private final ServiceProvider allServices;
    private final ServiceProvider parentServices;
    private final String displayName;
    private final ServiceProvider thisAsServiceProvider;

    private AtomicReference<State> state = new AtomicReference<State>(State.INIT);

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
        if (parents.length == 0) {
            this.parentServices = null;
            this.allServices = ownServices;
        } else {
            parentServices = setupParentServices(parents);
            allServices = new CompositeServiceProvider(ownServices, parentServices);
        }
        this.thisAsServiceProvider = allServices;

        findProviderMethods(this);
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
            ownServices.add(new FactoryMethodService(this, target, method));
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
                Service paramProvider = find(paramType, allServices);
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
        if (state.get() != State.INIT) {
            throw new IllegalStateException("Cannot add provide to service registry " + this + " as it is no longer mutable");
        }
    }

    private ServiceRegistration newRegistration() {
        return new ServiceRegistration() {
            @Override
            public <T> void add(Class<T> serviceType, T serviceInstance) {
                DefaultServiceRegistry.this.add(serviceType, serviceInstance);
            }

            @Override
            public void add(Class<?> serviceType) {
                ownServices.add(new ConstructorService(DefaultServiceRegistry.this, serviceType));
            }

            @Override
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
        if (state.compareAndSet(State.INIT, State.STARTED)) {
            ownServices.noLongerMutable();
        }
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

    private Service getService(Type serviceType) {
        serviceRequested();
        return find(serviceType, allServices);
    }

    @Override
    public <T> Factory<T> getFactory(Class<T> type) {
        assertValidServiceType(type);
        Service provider = getFactoryService(type);
        Factory<T> factory = provider == null ? null : (Factory<T>) provider.get();
        if (factory == null) {
            throw new UnknownServiceException(type, String.format("No factory for objects of type %s available in %s.", format(type), getDisplayName()));
        }
        return factory;
    }

    private Service getFactoryService(Class<?> serviceType) {
        serviceRequested();
        return allServices.getFactory(serviceType);
    }

    @Override
    public <T> List<T> getAll(Class<T> serviceType) throws ServiceLookupException {
        assertValidServiceType(serviceType);
        List<T> services = new ArrayList<T>();
        serviceRequested();
        allServices.getAll(serviceType, new InstanceUnpackingVisitor<T>(serviceType, services));
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
            delegate.add(serviceType.cast(service.get()));
        }
    }

    private static class CollectingVisitor implements ServiceProvider.Visitor {
        private final List<Service> delegate;

        private CollectingVisitor(List<Service> delegate) {
            this.delegate = delegate;
        }

        @Override
        public void visit(Service service) {
            delegate.add(service);
        }
    }

    @Override
    public <T> T newInstance(Class<T> type) {
        return getFactory(type).create();
    }

    private class OwnServices implements ServiceProvider {
        private final Map<Class<?>, List<ServiceProvider>> providersByType = new HashMap<Class<?>, List<ServiceProvider>>(16, 0.5f);
        private final CompositeStoppable stoppable = CompositeStoppable.stoppable();
        private ProviderAnalyser analyser = new ProviderAnalyser();

        @Override
        public Service getFactory(Class<?> type) {
            List<ServiceProvider> serviceProviders = getProviders(Factory.class);
            if (serviceProviders.isEmpty()) {
                return null;
            }
            if (serviceProviders.size() == 1) {
                return serviceProviders.get(0).getFactory(type);
            }

            List<Service> services = new ArrayList<Service>(serviceProviders.size());
            for (ServiceProvider serviceProvider : serviceProviders) {
                Service service = serviceProvider.getFactory(type);
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
        public Service getService(Type type) {
            List<ServiceProvider> serviceProviders = getProviders(unwrap(type));
            if (serviceProviders.isEmpty()) {
                return null;
            }
            if (serviceProviders.size() == 1) {
                return serviceProviders.get(0).getService(type);
            }

            List<Service> services = new ArrayList<Service>(serviceProviders.size());
            for (ServiceProvider serviceProvider : serviceProviders) {
                Service service = serviceProvider.getService(type);
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
        public Visitor getAll(Class<?> serviceType, ServiceProvider.Visitor visitor) {
            for (ServiceProvider serviceProvider : getProviders(serviceType)) {
                visitor = serviceProvider.getAll(serviceType, visitor);
            }
            return visitor;
        }

        @Override
        public void stop() {
            stoppable.stop();
        }

        public void add(ServiceProvider serviceProvider) {
            assertMutable();
            if (!(serviceProvider instanceof SingletonService)) {
                throw new UnsupportedOperationException("Unsupported service provider type: " + serviceProvider);
            }
            stoppable.add(serviceProvider);
            analyser.addProviderForClassHierarchy(((SingletonService) serviceProvider).serviceClass, serviceProvider);
        }

        public void noLongerMutable() {
            analyser = null;
        }

        private class ProviderAnalyser {
            private Set<Class<?>> seen = new HashSet<Class<?>>(4, 0.5f);

            public void addProviderForClassHierarchy(Class<?> serviceType, ServiceProvider serviceProvider) {
                analyseType(serviceType, serviceProvider);
                seen.clear();
            }

            private void analyseType(Class<?> type, ServiceProvider serviceProvider) {
                if (type == null || type == Object.class) {
                    return;
                }
                if (seen.add(type)) {
                    putServiceType(type, serviceProvider);
                    analyseType(type.getSuperclass(), serviceProvider);
                    for (Class<?> iface : type.getInterfaces()) {
                        analyseType(iface, serviceProvider);
                    }
                }
            }

            private void putServiceType(Class<?> type, ServiceProvider serviceProvider) {
                List<ServiceProvider> serviceProviders = providersByType.get(type);
                if (serviceProviders == null) {
                    serviceProviders = new ArrayList<ServiceProvider>(2);
                    providersByType.put(type, serviceProviders);
                }
                serviceProviders.add(serviceProvider);
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

    private static abstract class ManagedObjectServiceProvider<T> implements ServiceProvider {
        protected final DefaultServiceRegistry owner;
        private final Queue<ServiceProvider> dependents = new ConcurrentLinkedQueue<ServiceProvider>();
        private volatile T instance;

        protected ManagedObjectServiceProvider(DefaultServiceRegistry owner) {
            this.owner = owner;
        }

        protected final void setInstance(T instance) {
            this.instance = instance;
        }

        public final T getInstance() {
            T result = instance;
            if (result == null) {
                synchronized (this) {
                    result = instance;
                    if (result == null) {
                        result = instance = create();
                        assert instance != null : String.format("create() of %s returned null", toString());
                    }
                }
            }
            return result;
        }

        /**
         * Subclasses implement this method to create the service instance. It is never called concurrently and may not return null.
         */
        protected abstract T create();

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

    private static abstract class SingletonService extends ManagedObjectServiceProvider<Object> implements Service {
        private enum BindState {UNBOUND, BINDING, BOUND}

        final Type serviceType;
        final Class serviceClass;

        BindState state = BindState.UNBOUND;
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
         * Do any preparation work and validation to ensure that {@link #create()} ()} can be called later.
         * This method is never called concurrently.
         */
        protected void bind() {
        }

        @Override
        public Service getService(Type serviceType) {
            if (!isSatisfiedBy(serviceType, this.serviceType)) {
                return null;
            }
            return prepare();
        }

        @Override
        public Visitor getAll(Class<?> serviceType, ServiceProvider.Visitor visitor) {
            if (serviceType.isAssignableFrom(this.serviceClass)) {
                visitor.visit(prepare());
            }
            return visitor;
        }

        @Override
        public Service getFactory(Class<?> elementType) {
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
        private Service[] paramServices;
        private Service decorates;

        protected FactoryService(DefaultServiceRegistry owner, Type serviceType) {
            super(owner, serviceType);
        }

        protected abstract Type[] getParameterTypes();

        protected abstract Member getFactory();

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
                if (paramType.equals(ServiceRegistry.class)) {
                    paramServices[i] = owner.getThisAsService();
                } else if (paramType.equals(serviceType)) {
                    Service paramProvider = owner.find(paramType, owner.parentServices);
                    if (paramProvider == null) {
                        throw new ServiceCreationException(String.format("Cannot create service of type %s using %s.%s() as required service of type %s is not available in parent registries.",
                            format(serviceType),
                            getFactory().getDeclaringClass().getSimpleName(),
                            getFactory().getName(),
                            format(paramType)));
                    }
                    paramServices[i] = paramProvider;
                    decorates = paramProvider;
                } else {
                    Service paramProvider;
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
                    paramServices[i] = paramProvider;
                    paramProvider.requiredBy(this);
                }
            }
        }

        @Override
        protected Object create() {
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
        public Visitor getAll(Class<?> serviceType, final Visitor visitor) {
            super.getAll(serviceType, visitor);
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

    private class FactoryMethodService extends FactoryService {
        private final ServiceMethod method;
        private Object target;

        public FactoryMethodService(DefaultServiceRegistry owner, Object target, ServiceMethod method) {
            super(owner, method.getServiceType());
            this.target = target;
            this.method = method;
        }

        @Override
        public String getDisplayName() {
            return "Service " + format(method.getServiceType()) + " at " + method.getOwner().getSimpleName() + "." + method.getName() + "()";
        }

        @Override
        protected Type[] getParameterTypes() {
            return method.getParameterTypes();
        }

        @Override
        protected Member getFactory() {
            return method.getMethod();
        }

        @Override
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

    private Service getThisAsService() {
        return new Service() {
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
        };
    }

    private static class FixedInstanceService<T> extends SingletonService {
        public FixedInstanceService(DefaultServiceRegistry owner, Class<T> serviceType, T serviceInstance) {
            super(owner, serviceType);
            setInstance(serviceInstance);
        }

        @Override
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

        @Override
        public String getDisplayName() {
            return "Service " + format(serviceType);
        }
    }

    private static class CompositeServiceProvider implements ServiceProvider {
        private final ServiceProvider[] serviceProviders;

        private CompositeServiceProvider(ServiceProvider... serviceProviders) {
            this.serviceProviders = serviceProviders;
        }

        @Override
        public Service getService(Type serviceType) {
            for (ServiceProvider serviceProvider : serviceProviders) {
                Service service = serviceProvider.getService(serviceType);
                if (service != null) {
                    return service;
                }
            }
            return null;
        }

        @Override
        public Service getFactory(Class<?> type) {
            for (ServiceProvider serviceProvider : serviceProviders) {
                Service factory = serviceProvider.getFactory(type);
                if (factory != null) {
                    return factory;
                }
            }
            return null;
        }

        @Override
        public Visitor getAll(Class<?> serviceType, Visitor visitor) {
            for (ServiceProvider serviceProvider : serviceProviders) {
                visitor = serviceProvider.getAll(serviceType, visitor);
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
        public Service getFactory(Class<?> serviceType) {
            return parent.getFactory(serviceType);
        }

        @Override
        public Service getService(Type serviceType) {
            return parent.getService(serviceType);
        }

        @Override
        public Visitor getAll(Class<?> serviceType, Visitor visitor) {
            return parent.getAll(serviceType, visitor);
        }

        @Override
        public void stop() {
        }
    }

    public Service find(Type serviceType, ServiceProvider serviceProvider) {
        if (serviceType instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) serviceType;
            Type rawType = parameterizedType.getRawType();
            if (rawType.equals(Factory.class)) {
                final Type typeArg = parameterizedType.getActualTypeArguments()[0];
                return getFactoryService(typeArg, serviceProvider);
            }
            if (rawType instanceof Class) {
                if (((Class<?>) rawType).isAssignableFrom(List.class)) {
                    Type typeArg = parameterizedType.getActualTypeArguments()[0];
                    return getCollectionService(typeArg, serviceProvider);
                }
                assertValidServiceType((Class<?>) rawType);
                return serviceProvider.getService(serviceType);
            }
        }
        if (serviceType instanceof Class<?>) {
            assertValidServiceType((Class<?>) serviceType);
            return serviceProvider.getService(serviceType);
        }

        throw new ServiceValidationException(String.format("Locating services with type %s is not supported.", format(serviceType)));
    }

    private Service getFactoryService(Type type, ServiceProvider serviceProvider) {
        if (type instanceof Class) {
            return serviceProvider.getFactory((Class) type);
        }
        if (type instanceof WildcardType) {
            final WildcardType wildcardType = (WildcardType) type;
            if (wildcardType.getLowerBounds().length == 1 && wildcardType.getUpperBounds().length == 1) {
                if (wildcardType.getLowerBounds()[0] instanceof Class && wildcardType.getUpperBounds()[0].equals(Object.class)) {
                    return serviceProvider.getFactory((Class<?>) wildcardType.getLowerBounds()[0]);
                }
            }
            if (wildcardType.getLowerBounds().length == 0 && wildcardType.getUpperBounds().length == 1) {
                if (wildcardType.getUpperBounds()[0] instanceof Class) {
                    return serviceProvider.getFactory((Class<?>) wildcardType.getUpperBounds()[0]);
                }
            }
        }
        throw new ServiceValidationException(String.format("Locating services with type %s is not supported.", format(type)));
    }

    private Service getCollectionService(Type elementType, ServiceProvider serviceProvider) {
        if (elementType instanceof Class) {
            Class<?> elementClass = (Class<?>) elementType;
            return getCollectionService(elementClass, serviceProvider);
        }
        if (elementType instanceof WildcardType) {
            WildcardType wildcardType = (WildcardType) elementType;
            if (wildcardType.getUpperBounds()[0] instanceof Class && wildcardType.getLowerBounds().length == 0) {
                Class<?> elementClass = (Class<?>) wildcardType.getUpperBounds()[0];
                return getCollectionService(elementClass, serviceProvider);
            }
        }
        throw new ServiceValidationException(String.format("Locating services with type %s is not supported.", format(elementType)));
    }

    private Service getCollectionService(Class<?> elementClass, ServiceProvider serviceProvider) {
        assertValidServiceType(elementClass);
        List<Service> providers = new ArrayList<Service>();
        serviceProvider.getAll(elementClass, new CollectingVisitor(providers));
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
}
