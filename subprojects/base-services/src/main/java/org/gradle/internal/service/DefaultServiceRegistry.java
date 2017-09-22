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
import org.gradle.api.specs.Spec;
import org.gradle.internal.Factory;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.util.BiFunction;

import javax.annotation.Nullable;
import java.io.Closeable;
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
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

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
    private final static ServiceRegistry[] NO_PARENTS = new ServiceRegistry[0];
    private final static ServiceProvider[] NO_DEPENDENTS = new ServiceProvider[0];
    private final static Object[] NO_PARAMS = new Object[0];

    private static final ConcurrentMap<Type, BiFunction<ServiceProvider, LookupContext, Provider>> SERVICE_TYPE_PROVIDER_CACHE = new ConcurrentHashMap<Type, BiFunction<ServiceProvider, LookupContext, Provider>>();
    private final Map<Type, ServiceProvider> providerCache = new IdentityHashMap<Type, ServiceProvider>();

    private final Object lock = new Object();
    private final OwnServices ownServices;
    private final Provider allServices;
    private final Provider parentServices;
    private final String displayName;
    private boolean closed;
    private boolean mutable = true; // access under lock

    private Provider asParentServicesProvider;

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
            this.allServices = CachingProvider.of(ownServices);
        } else {
            parentServices = setupParentServices(parents);
            allServices = new CompositeProvider(CachingProvider.of(ownServices), parentServices);
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
        if (asParentServicesProvider == null) {
            asParentServicesProvider = CachingProvider.of(new ParentServices(this));
        }
        return asParentServicesProvider;
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
            ownServices.add(new DecoratorMethodService(target, method));
        }
        for (ServiceMethod method : methods.factories) {
            ownServices.add(new FactoryMethodService(target, method));
        }
        for (ServiceMethod method : methods.configurers) {
            applyConfigureMethod(method, target);
        }
    }

    private void applyConfigureMethod(ServiceMethod method, Object target) {
        Object[] params = new Object[method.getParameterTypes().length];
        DefaultLookupContext context = new DefaultLookupContext();
        for (int i = 0; i < method.getParameterTypes().length; i++) {
            Type paramType = method.getParameterTypes()[i];
            if (paramType.equals(ServiceRegistration.class)) {
                params[i] = newRegistration();
            } else {
                ServiceProvider paramProvider = context.find(paramType, allServices);
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
        if (!mutable) {
            throw new IllegalStateException("Cannot add provide to service registry " + this + " as it is no longer mutable");
        }
    }

    private ServiceRegistration newRegistration() {
        return new ServiceRegistration() {
            public <T> void add(Class<T> serviceType, T serviceInstance) {
                DefaultServiceRegistry.this.add(serviceType, serviceInstance);
            }

            public void add(Class<?> serviceType) {
                ownServices.add(new ConstructorService(serviceType));
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
        ownServices.add(new FixedInstanceService<T>(serviceType, serviceInstance));
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
        synchronized (lock) {
            try {
                CompositeStoppable.stoppable(allServices).stop();
            } finally {
                closed = true;
            }
        }
    }

    public boolean isClosed() {
        return closed;
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

    public boolean hasService(Class<?> serviceType) {
        assertValidServiceType(serviceType);
        return allServices.hasService(serviceType);
    }

    public <T> List<T> getAll(Class<T> serviceType) throws ServiceLookupException {
        synchronized (lock) {
            mutable = false;
            if (closed) {
                throw new IllegalStateException(String.format("Cannot locate service of type %s, as %s has been closed.", format(serviceType), getDisplayName()));
            }
            if (!hasService(serviceType)) {
                return Collections.emptyList();
            }
            List<ServiceProvider> providers = new ArrayList<ServiceProvider>();
            DefaultLookupContext context = new DefaultLookupContext();
            allServices.getAll(context, serviceType, providers);
            List<T> services = new ArrayList<T>(providers.size());
            for (ServiceProvider provider : providers) {
                services.add(serviceType.cast(provider.get()));
            }
            return services;
        }
    }

    public <T> T get(Class<T> serviceType) throws UnknownServiceException, ServiceLookupException {
        return serviceType.cast(doGet(serviceType));
    }

    public Object get(Type serviceType) throws UnknownServiceException, ServiceLookupException {
        return doGet(serviceType);
    }


    private Object doGet(Type serviceType) throws IllegalArgumentException {
        synchronized (lock) {
            mutable = false;
            if (closed) {
                throw new IllegalStateException(String.format("Cannot locate service of type %s, as %s has been closed.", format(serviceType), getDisplayName()));
            }
            ServiceProvider provider = providerCache.get(serviceType);
            if (provider == null) {
                provider = getServiceProvider(serviceType);
                providerCache.put(serviceType, provider);
            }
            return provider.get();
        }
    }

    private ServiceProvider getServiceProvider(Type serviceType) {
        Type lookupType = extractServiceType(serviceType);
        ServiceProvider provider = hasService(unwrap(lookupType)) ? new DefaultLookupContext().find(serviceType, allServices) : null;
        if (provider == null) {
            throw new UnknownServiceException(serviceType, String.format("No service of type %s available in %s.", format(serviceType), getDisplayName()));
        }
        return provider;
    }

    private static Type extractServiceType(Type mayBeFactoryType) {
        Type serviceType = mayBeFactoryType;
        if (mayBeFactoryType instanceof ParameterizedType) {
            Type rawType = ((ParameterizedType) mayBeFactoryType).getRawType();
            if (rawType == List.class) {
                serviceType = ((ParameterizedType) mayBeFactoryType).getActualTypeArguments()[0];
            }
        }
        return serviceType;
    }

    public <T> Factory<T> getFactory(Class<T> type) {
        synchronized (lock) {
            if (closed) {
                throw new IllegalStateException(String.format("Cannot locate factory for objects of type %s, as %s has been closed.", format(type), getDisplayName()));
            }
            if (hasService(Factory.class)) {
                DefaultLookupContext context = new DefaultLookupContext();
                ServiceProvider factory = allServices.getFactory(context, type);
                if (factory != null) {
                    return (Factory<T>) factory.get();
                }
            }
            throw new UnknownServiceException(type, String.format("No factory for objects of type %s available in %s.", format(type), getDisplayName()));
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
     * Provides a set or zero or more services.
     */
    interface Provider extends Stoppable {
        /**
         * Locates a service instance of the given type. Returns null if this provider does not provide a service of this type.
         */
        ServiceProvider getService(LookupContext context, TypeSpec serviceType);

        /**
         * Locates a factory for services of the given type. Returns null if this provider does not provide any services of this type.
         */
        ServiceProvider getFactory(LookupContext context, Class<?> type);

        /**
         * Collects all services of the given type.
         */
        void getAll(LookupContext context, Class<?> serviceType, List<ServiceProvider> result);

        boolean hasService(Class<?> type);
    }

    private class OwnServices implements Provider {
        private List<Provider> providers;
        private Set<Class<?>> serviceTypes;

        @Override
        public ServiceProvider getFactory(LookupContext context, Class<?> type) {
            if (!hasService(Factory.class)) {
                return null;
            }
            List<ServiceProvider> candidates = new ArrayList<ServiceProvider>();
            for (Provider provider : providers) {
                ServiceProvider factory = provider.getFactory(context, type);
                if (factory != null) {
                    candidates.add(factory);
                }
            }
            if (candidates.size() == 0) {
                return null;
            }
            if (candidates.size() == 1) {
                return candidates.get(0);
            }

            Set<String> descriptions = new TreeSet<String>();
            for (ServiceProvider candidate : candidates) {
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
        public ServiceProvider getService(LookupContext context, TypeSpec serviceType) {
            if (!hasService(unwrap(serviceType.getType()))) {
                return null;
            }
            ServiceProvider singleCandidate = null;
            List<ServiceProvider> candidates = null;
            for (Provider provider : providers) {
                ServiceProvider service = provider.getService(context, serviceType);
                if (service != null) {
                    if (singleCandidate == null) {
                        singleCandidate = service;
                    } else {
                        if (candidates == null) {
                            candidates = new ArrayList<ServiceProvider>(2);
                            candidates.add(singleCandidate);
                        }
                        candidates.add(service);
                    }
                }
            }

            if (candidates == null && singleCandidate == null) {
                return null;
            }
            if (candidates == null) {
                return singleCandidate;
            }

            Set<String> descriptions = new TreeSet<String>();
            for (ServiceProvider candidate : candidates) {
                descriptions.add(candidate.getDisplayName());
            }

            Formatter formatter = new Formatter();
            formatter.format("Multiple services of type %s available in %s:", format(serviceType.getType()), getDisplayName());
            for (String description : descriptions) {
                formatter.format("%n   - %s", description);
            }
            throw new ServiceLookupException(formatter.toString());
        }

        @Override
        public void getAll(LookupContext context, Class<?> serviceType, List<ServiceProvider> result) {
            if (!hasService(serviceType)) {
                return;
            }
            for (Provider provider : providers) {
                provider.getAll(context, serviceType, result);
            }
        }

        public boolean hasService(Class<?> serviceType) {
            return serviceTypes != null && serviceTypes.contains(serviceType);
        }

        @Override
        public void stop() {
            if (providers == null) {
                return;
            }
            CompositeStoppable.stoppable(providers).stop();
        }

        public void add(Provider provider) {
            assertMutable();
            if (providers == null) {
                providers = new ArrayList<Provider>();
                serviceTypes = new HashSet<Class<?>>();
                serviceTypes.add(Object.class);
            }
            this.providers.add(provider);
            if (provider instanceof SingletonService) {
                addServiceType(((SingletonService) provider).serviceClass);
            } else {
                throw new UnsupportedOperationException("Unsupported service provider type: " + provider);
            }
        }

        private void addServiceType(Class<?> serviceType) {
            if (serviceType!=null && serviceTypes.add(serviceType)) {
                addServiceType(serviceType.getSuperclass());
                Class<?>[] interfaces = serviceType.getInterfaces();
                if (interfaces!=null) {
                    for (Class<?> intf : interfaces) {
                        addServiceType(intf);
                    }
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
        private T instance;
        private Set<Provider> dependents;

        protected void setInstance(T instance) {
            this.instance = instance;
        }

        public T getInstance() {
            if (instance == null) {
                instance = create();
                assert instance != null : String.format("create() of %s returned null", toString());
            }
            return instance;
        }

        protected abstract T create();

        public void requiredBy(Provider provider) {
            if (dependents == null) {
                dependents = new HashSet<Provider>();
            }
            dependents.add(provider);
        }

        public void stop() {
            try {
                if (instance != null) {
                    CompositeStoppable.stoppable(dependents == null ? Collections.emptyList() : dependents).add(instance).stop();
                }
            } finally {
                if (dependents != null) {
                    dependents.clear();
                }
                instance = null;
            }
        }
    }

    private static abstract class SingletonService extends ManagedObjectProvider<Object> implements ServiceProvider {
        final Type serviceType;
        final Class serviceClass;

        // cached for performance
        Class factoryElementType;
        boolean bound;

        SingletonService(Type serviceType) {
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

        private ServiceProvider prepare(LookupContext context) {
            if (!bound) {
                bind(context);
                bound = true;
            }
            return this;
        }

        protected void bind(LookupContext context) {
        }

        @Override
        public ServiceProvider getService(LookupContext context, TypeSpec serviceType) {
            if (!serviceType.isSatisfiedBy(this.serviceType)) {
                return null;
            }
            return prepare(context);
        }

        @Override
        public void getAll(LookupContext context, Class<?> serviceType, List<ServiceProvider> result) {
            if (serviceType.isAssignableFrom(this.serviceClass)) {
                result.add(prepare(context));
            }
        }

        @Override
        public ServiceProvider getFactory(LookupContext context, Class<?> elementType) {
            if (!isFactory(serviceType, elementType)) {
                return null;
            }
            return prepare(context);
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

        @Override
        public boolean hasService(Class<?> type) {
            return type.isAssignableFrom(serviceClass);
        }
    }

    private abstract class FactoryService extends SingletonService {
        private ServiceProvider[] paramProviders;

        protected FactoryService(Type serviceType) {
            super(serviceType);
        }

        protected abstract Type[] getParameterTypes();

        protected abstract Member getFactory();

        @Override
        protected void bind(LookupContext context) {
            Type[] parameterTypes = getParameterTypes();
            if (parameterTypes.length==0) {
                paramProviders = NO_DEPENDENTS;
                return;
            }
            paramProviders = new ServiceProvider[parameterTypes.length];
            for (int i = 0; i < parameterTypes.length; i++) {
                Type paramType = parameterTypes[i];
                try {
                    if (paramType.equals(ServiceRegistry.class)) {
                        paramProviders[i] = getThisAsProvider();
                    } else {
                        ServiceProvider paramProvider = context.find(paramType, allServices);
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
                } catch (ServiceValidationException e) {
                    throw new ServiceCreationException(String.format("Cannot create service of type %s using %s.%s() as there is a problem with parameter #%s of type %s.",
                        format(serviceType),
                        getFactory().getDeclaringClass().getSimpleName(),
                        getFactory().getName(),
                        i + 1,
                        format(paramType)), e);
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

        public FactoryMethodService(Object target, ServiceMethod method) {
            super(method.getServiceType());
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

    private ServiceProvider getThisAsProvider() {
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
        public FixedInstanceService(Class<T> serviceType, T serviceInstance) {
            super(serviceType);
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

    private class ConstructorService extends FactoryService {
        private final Constructor<?> constructor;

        private ConstructorService(Class<?> serviceType) {
            super(serviceType);
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

    private class DecoratorMethodService extends SingletonService {
        private final ServiceMethod method;
        private Object target;
        private ServiceProvider paramProvider;

        public DecoratorMethodService(Object target, ServiceMethod method) {
            super(method.getServiceType());
            this.target = target;
            this.method = method;
        }

        public String getDisplayName() {
            return "Service " + format(method.getServiceType()) + " at " + method.getOwner().getSimpleName() + "." + method.getName() + "()";
        }

        @Override
        protected void bind(LookupContext context) {
            Type paramType = method.getParameterTypes()[0];
            DefaultLookupContext parentLookupContext = new DefaultLookupContext();
            paramProvider = parentLookupContext.find(paramType, parentServices);
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

    private static class CachingProvider implements Provider {
        private static final Object ABSENT = new Object();
        private final ConcurrentMap<Object, Object> seen = new ConcurrentHashMap<Object, Object>();
        private final ConcurrentMap<Class<?>, List<ServiceProvider>> allServicesCache = new ConcurrentHashMap<Class<?>, List<ServiceProvider>>();
        private final Map<Class<?>, Boolean> serviceTypes = new ConcurrentHashMap<Class<?>, Boolean>();

        private final Provider delegate;

        private CachingProvider(Provider delegate) {
            this.delegate = delegate;
        }

        private static Provider of(Provider delegate) {
            if (delegate instanceof CachingProvider) {
                return delegate;
            }
            return new CachingProvider(delegate);
        }

        @Override
        public ServiceProvider getService(LookupContext context, TypeSpec serviceType) {
            Object cached = seen.get(serviceType);
            if (cached != null) {
                return cached == ABSENT ? null : (ServiceProvider) cached;
            }
            ServiceProvider service = delegate.getService(context, serviceType);
            return cacheServiceProvider(serviceType, service);
        }

        private ServiceProvider cacheServiceProvider(Object key, ServiceProvider service) {
            seen.putIfAbsent(key, service == null ? ABSENT : service);
            return service;
        }

        @Override
        public ServiceProvider getFactory(LookupContext context, Class<?> type) {
            Object cached = seen.get(type);
            if (cached != null) {
                return cached == ABSENT ? null : (ServiceProvider) cached;
            }
            ServiceProvider service = delegate.getFactory(context, type);
            return cacheServiceProvider(type, service);
        }

        @Override
        public void getAll(LookupContext context, Class<?> serviceType, List<ServiceProvider> result) {
            List<ServiceProvider> services = allServicesCache.get(serviceType);
            if (services != null) {
                result.addAll(services);
                return;
            }
            ArrayList<ServiceProvider> tmp = new ArrayList<ServiceProvider>();
            delegate.getAll(context, serviceType, tmp);
            allServicesCache.putIfAbsent(serviceType, tmp);
            if (!tmp.isEmpty()) {
                result.addAll(tmp);
            }
        }

        @Override
        public boolean hasService(Class<?> type) {
            Boolean val = serviceTypes.get(type);
            if (val != null) {
                return val;
            }
            val = delegate.hasService(type);
            serviceTypes.put(type, val);
            return val;
        }

        @Override
        public void stop() {
            delegate.stop();
            seen.clear();
            allServicesCache.clear();
        }
    }

    private static class CompositeProvider implements Provider {
        private final Provider[] providers;

        private CompositeProvider(Provider... providers) {
            this.providers = providers;
        }

        @Override
        public ServiceProvider getService(LookupContext context, TypeSpec serviceType) {
            if (hasService(unwrap(extractServiceType(serviceType.getType())))) {
                for (Provider provider : providers) {
                    ServiceProvider service = provider.getService(context, serviceType);
                    if (service != null) {
                        return service;
                    }
                }
            }
            return null;
        }

        @Override
        public ServiceProvider getFactory(LookupContext context, Class<?> type) {
            if (hasService(Factory.class)) {
                for (Provider provider : providers) {
                    ServiceProvider factory = provider.getFactory(context, type);
                    if (factory != null) {
                        return factory;
                    }
                }
            }
            return null;
        }

        @Override
        public void getAll(LookupContext context, Class<?> serviceType, List<ServiceProvider> result) {
            if (hasService(serviceType)) {
                for (Provider provider : providers) {
                    provider.getAll(context, serviceType, result);
                }
            }
        }

        @Override
        public boolean hasService(Class<?> type) {
            for (Provider provider : providers) {
                if (provider.hasService(type)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public void stop() {
            try {
                CompositeStoppable.stoppable(Arrays.asList(providers)).stop();
            } finally {
                for (int i = 0; i < providers.length; i++) {
                    providers[i] = null;
                }
            }
        }
    }

    private static class ParentServices implements Provider {
        private final ServiceRegistry parent;

        private ParentServices(ServiceRegistry parent) {
            this.parent = parent;
        }

        @Override
        public ServiceProvider getFactory(LookupContext context, Class<?> type) {
            try {
                Factory<?> factory = parent.getFactory(type);
                assert factory != null : String.format("parent returned null for factory type '%s'", type.getName());
                return wrap(factory);
            } catch (UnknownServiceException e) {
                if (!e.getType().equals(type)) {
                    throw e;
                }
            }
            return null;
        }

        @Override
        public ServiceProvider getService(LookupContext context, TypeSpec serviceType) {
            try {
                Object service = parent.get(serviceType.getType());
                assert service != null : String.format("parent returned null for service type %s", format(serviceType.getType()));
                return wrap(service);
            } catch (UnknownServiceException e) {
                if (!e.getType().equals(serviceType.getType())) {
                    throw e;
                }
            }
            return null;
        }

        private ServiceProvider wrap(final Object instance) {
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
        public void getAll(LookupContext context, Class<?> serviceType, List<ServiceProvider> result) {
            List<?> services = parent.getAll(serviceType);
            assert services != null : String.format("parent returned null for services of type %s", format(serviceType));
            for (Object service : services) {
                result.add(wrap(service));
            }
        }

        @Override
        public boolean hasService(Class<?> type) {
            return parent.hasService(type);
        }

        @Override
        public void stop() {
        }
    }

    /**
     * Maintains state used while locating services and their dependencies.
     */
    interface LookupContext {
        @Nullable
        ServiceProvider find(Type type, Provider provider);
    }

    interface TypeSpec extends Spec<Type> {
        Type getType();
    }

    private static class ClassSpec implements TypeSpec {
        private final Class<?> type;

        private ClassSpec(Class<?> type) {
            this.type = type;
        }

        public Type getType() {
            return type;
        }

        public boolean isSatisfiedBy(Type element) {
            if (element instanceof ParameterizedType) {
                ParameterizedType parameterizedType = (ParameterizedType) element;
                if (parameterizedType.getRawType() instanceof Class) {
                    return type.isAssignableFrom((Class) parameterizedType.getRawType());
                }
            } else if (element instanceof Class) {
                Class<?> other = (Class<?>) element;
                return type.isAssignableFrom(other);
            }
            return false;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            ClassSpec classSpec = (ClassSpec) o;

            return type != null ? type.equals(classSpec.type) : classSpec.type == null;

        }

        @Override
        public int hashCode() {
            return type.hashCode();
        }
    }

    private static class ParameterizedTypeSpec implements TypeSpec {
        private final Type type;
        private final TypeSpec rawType;
        private final List<TypeSpec> paramSpecs;

        private ParameterizedTypeSpec(Type type, TypeSpec rawType, List<TypeSpec> paramSpecs) {
            this.type = type;
            this.rawType = rawType;
            this.paramSpecs = paramSpecs;
        }

        public Type getType() {
            return type;
        }

        public boolean isSatisfiedBy(Type element) {
            if (element instanceof ParameterizedType) {
                ParameterizedType parameterizedType = (ParameterizedType) element;
                if (!rawType.isSatisfiedBy(parameterizedType.getRawType())) {
                    return false;
                }
                for (int i = 0; i < parameterizedType.getActualTypeArguments().length; i++) {
                    Type type = parameterizedType.getActualTypeArguments()[i];
                    if (!paramSpecs.get(i).isSatisfiedBy(type)) {
                        return false;
                    }
                }
                return true;
            }
            return false;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            ParameterizedTypeSpec that = (ParameterizedTypeSpec) o;

            if (!type.equals(that.type)) {
                return false;
            }
            if (!rawType.equals(that.rawType)) {
                return false;
            }
            return paramSpecs.equals(that.paramSpecs);

        }

        @Override
        public int hashCode() {
            int result = type.hashCode();
            result = 31 * result + rawType.hashCode();
            result = 31 * result + paramSpecs.hashCode();
            return result;
        }
    }


    private static class DefaultLookupContext implements LookupContext {
        private final Set<Type> visiting = new HashSet<Type>();

        public ServiceProvider find(Type serviceType, Provider provider) {
            if (!visiting.add(serviceType)) {
                throw new ServiceValidationException(String.format("Cycle in dependencies of service of type %s.", format(serviceType)));
            }
            try {
                return getServiceProvider(serviceType, provider);
            } finally {
                visiting.remove(serviceType);
            }
        }

        public ServiceProvider getServiceProvider(Type serviceType, Provider provider) {
            BiFunction<ServiceProvider, LookupContext, Provider> function = SERVICE_TYPE_PROVIDER_CACHE.get(serviceType);
            if (function == null) {
                function = createServiceProviderFactory(serviceType);
                SERVICE_TYPE_PROVIDER_CACHE.putIfAbsent(serviceType, function);
            }
            return function.apply(this, provider);
        }

        private static BiFunction<ServiceProvider, LookupContext, Provider> createServiceProviderFactory(final Type serviceType) {
            if (serviceType instanceof ParameterizedType) {
                ParameterizedType parameterizedType = (ParameterizedType) serviceType;
                Type rawType = parameterizedType.getRawType();
                if (rawType.equals(Factory.class)) {
                    final Type typeArg = parameterizedType.getActualTypeArguments()[0];
                    if (typeArg instanceof Class) {
                        return new BiFunction<ServiceProvider, LookupContext, Provider>() {
                            @Override
                            public ServiceProvider apply(LookupContext lookupContext, Provider provider) {
                                return provider.getFactory(lookupContext, (Class) typeArg);
                            }
                        };
                    }
                    if (typeArg instanceof WildcardType) {
                        final WildcardType wildcardType = (WildcardType) typeArg;
                        if (wildcardType.getLowerBounds().length == 1 && wildcardType.getUpperBounds().length == 1) {
                            if (wildcardType.getLowerBounds()[0] instanceof Class && wildcardType.getUpperBounds()[0].equals(Object.class)) {
                                return new BiFunction<ServiceProvider, LookupContext, Provider>() {
                                    @Override
                                    public ServiceProvider apply(LookupContext lookupContext, Provider provider) {
                                        return provider.getFactory(lookupContext, (Class<?>) wildcardType.getLowerBounds()[0]);
                                    }
                                };
                            }
                        }
                        if (wildcardType.getLowerBounds().length == 0 && wildcardType.getUpperBounds().length == 1) {
                            if (wildcardType.getUpperBounds()[0] instanceof Class) {
                                return new BiFunction<ServiceProvider, LookupContext, Provider>() {
                                    @Override
                                    public ServiceProvider apply(LookupContext lookupContext, Provider provider) {
                                        return provider.getFactory(lookupContext, (Class<?>) wildcardType.getUpperBounds()[0]);
                                    }
                                };
                            }
                        }
                    }
                }
                if (rawType instanceof Class && ((Class<?>) rawType).isAssignableFrom(List.class)) {
                    Type typeArg = parameterizedType.getActualTypeArguments()[0];
                    if (typeArg instanceof Class) {
                        return new ServicesWithTypeLookup((Class<?>) typeArg);
                    }
                    if (typeArg instanceof WildcardType) {
                        WildcardType wildcardType = (WildcardType) typeArg;
                        if (wildcardType.getUpperBounds()[0] instanceof Class && wildcardType.getLowerBounds().length == 0) {
                            return new ServicesWithTypeLookup((Class<?>) wildcardType.getUpperBounds()[0]);
                        }
                    }
                }
            }

            final TypeSpec serviceTypeSpec = toSpec(serviceType);
            return new BiFunction<ServiceProvider, LookupContext, Provider>() {
                @Override
                public ServiceProvider apply(LookupContext lookupContext, Provider provider) {
                    return provider.getService(lookupContext, serviceTypeSpec);
                }
            };
        }

        static TypeSpec toSpec(Type serviceType) {
            if (serviceType instanceof ParameterizedType) {
                ParameterizedType parameterizedType = (ParameterizedType) serviceType;
                List<TypeSpec> paramSpecs = new ArrayList<TypeSpec>();
                for (Type paramType : parameterizedType.getActualTypeArguments()) {
                    paramSpecs.add(toSpec(paramType));
                }
                return new ParameterizedTypeSpec(serviceType, toSpec(parameterizedType.getRawType()), paramSpecs);
            } else if (serviceType instanceof Class) {
                Class<?> serviceClass = (Class<?>) serviceType;
                assertValidServiceType(serviceClass);
                return new ClassSpec(serviceClass);
            }

            throw new ServiceValidationException(String.format("Locating services with type %s is not supported.", format(serviceType)));
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

        private static class ServicesWithTypeLookup implements BiFunction<ServiceProvider, LookupContext, Provider> {
            private final Class<?> elementClass;

            ServicesWithTypeLookup(Class<?> elementClass) {
                this.elementClass = elementClass;
            }

            @Override
            public ServiceProvider apply(final LookupContext lookupContext, final Provider provider) {
                List<ServiceProvider> providers = new ArrayList<ServiceProvider>();
                provider.getAll(lookupContext, elementClass, providers);
                List<Object> services = new ArrayList<Object>(providers.size());
                for (ServiceProvider serviceProvider : providers) {
                    services.add(serviceProvider.get());
                }
                return new CollectionServiceProvider(elementClass, services, providers);
            }
        }
    }

    private static void assertValidServiceType(Class<?> serviceClass) {
        if (serviceClass.isArray()) {
            throw new ServiceValidationException("Locating services with array type is not supported.");
        }
        if (serviceClass.isAnnotation()) {
            throw new ServiceValidationException("Locating services with annotation type is not supported.");
        }
    }
}
