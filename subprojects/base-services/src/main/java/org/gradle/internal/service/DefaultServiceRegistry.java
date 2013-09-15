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
import org.gradle.api.Nullable;
import org.gradle.internal.CompositeStoppable;
import org.gradle.internal.Factory;
import org.gradle.internal.Stoppable;
import org.gradle.internal.UncheckedException;

import java.lang.reflect.*;
import java.util.*;

/**
 * A hierarchical {@link ServiceRegistry} implementation.
 *
 * <p>Subclasses can register services by:</p>
 *
 * <li>Calling {@link #add(Class, Object)} to register a service instance.</li>
 *
 * <li>Calling {@link #add(ServiceRegistry)} to register a set of services.</li>
 *
 * <li>Calling {@link #addProvider(Object)} to register a service provider bean.</li>
 *
 * <li>Adding a factory method. A factory method should have a name that starts with 'create', and have a non-void return type. For example, <code>protected SomeService
 * createSomeService() { .... }</code>. Parameters are injected using services from this registry.</li>
 *
 * <li>Adding a decorator method. A decorator method should have a name that starts with 'decorate', take a single parameter, and a have a non-void return type. The before invoking the method, the
 * parameter is located in the parent service registry and then passed to the method.</li>
 *
 * </ul>
 *
 * <p>Service instances are created on demand. {@link #getFactory(Class)} looks for a service instance which implements {@code Factory<T>} where {@code T} is the expected type.</p>.
 *
 * <p>Service registries are arranged in a hierarchy. If a service of a given type cannot be located, the registry uses its parent registry, if any, to locate the service.</p>
 */
public class DefaultServiceRegistry implements ServiceRegistry {
    private final CompositeProvider allServices = new CompositeProvider();
    private final OwnServices ownServices;
    private final List<Provider> registeredProviders;
    private final CompositeProvider parentServices;
    private boolean closed;

    public DefaultServiceRegistry() {
        this(new ServiceRegistry[0]);
    }

    public DefaultServiceRegistry(ServiceRegistry... parents) {
        this.parentServices = parents.length == 0 ? null : new CompositeProvider();
        this.ownServices = new OwnServices();
        allServices.providers.add(ownServices);
        if (parentServices != null) {
            allServices.providers.add(parentServices);
            for (ServiceRegistry parent : parents) {
                parentServices.providers.add(new ParentServices(parent));
            }
        }
        registeredProviders = allServices.providers.subList(1, 1);

        findProviderMethods(this);
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

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

    private void findProviderMethods(Object target) {
        Set<String> methods = new HashSet<String>();
        for (Class<?> type = target.getClass(); type != Object.class; type = type.getSuperclass()) {
            findDecoratorMethods(target, type, methods, ownServices);
            findFactoryMethods(target, type, methods, ownServices);
        }
    }

    private void findFactoryMethods(Object target, Class<?> type, Set<String> factoryMethods, OwnServices ownServices) {
        for (Method method : type.getDeclaredMethods()) {
            if (method.getName().startsWith("create")
                    && method.getReturnType() != Void.class
                    && !Modifier.isStatic(method.getModifiers())) {
                if (factoryMethods.add(method.getName())) {
                    ownServices.add(new FactoryMethodService(target, method));
                }
            }
        }
    }

    private void findDecoratorMethods(Object target, Class<?> type, Set<String> decoratorMethods, OwnServices ownServices) {
        for (Method method : type.getDeclaredMethods()) {
            if (method.getName().startsWith("create")
                    && method.getParameterTypes().length == 1
                    && method.getReturnType() != Void.class
                    && method.getParameterTypes()[0].equals(method.getReturnType())) {
                if (parentServices == null) {
                    throw new ServiceLookupException("Cannot use decorator methods when no parent registry is provided.");
                }
                if (decoratorMethods.add(method.getName())) {
                    ownServices.add(new DecoratorMethodService(target, method));
                }
            }
        }
    }

    /**
     * Adds services to this container using the given action.
     */
    public void register(Action<? super ServiceRegistration> action) {
        action.execute(new ServiceRegistration(){
            public <T> void add(Class<T> serviceType, T serviceInstance) {
                DefaultServiceRegistry.this.add(serviceType, serviceInstance);
            }

            public void addProvider(Object provider) {
                DefaultServiceRegistry.this.addProvider(provider);
            }
        });
    }

    /**
     * Adds a set of services to this registry. The given registry is closed when this registry is closed.
     */
    public DefaultServiceRegistry add(ServiceRegistry nested) {
        registeredProviders.add(new NestedServices(nested));
        return this;
    }

    /**
     * Adds a service to this registry. The given object is closed when this registry is closed.
     */
    public <T> DefaultServiceRegistry add(Class<T> serviceType, final T serviceInstance) {
        ownServices.add(new FixedInstanceService<T>(serviceType, serviceInstance));
        return this;
    }

    /**
     * Adds a service provider bean to this registry. This provider may define factory and decorator methods.
     */
    public DefaultServiceRegistry addProvider(Object provider) {
        findProviderMethods(provider);
        return this;
    }

    /**
     * Closes all services for this registry. For each service, if the service has a public void close() method, that method is called to close the service.
     */
    public void close() {
        try {
            CompositeStoppable.stoppable(allServices).stop();
        } finally {
            closed = true;
        }
    }

    private static String format(Type type) {
        if (type instanceof Class) {
            Class<?> aClass = (Class) type;
            return aClass.getSimpleName();
        }
        return type.toString();
    }

    public <T> List<T> getAll(Class<T> serviceType) throws ServiceLookupException {
        if (closed) {
            throw new IllegalStateException(String.format("Cannot locate service of type %s, as %s has been closed.", format(serviceType), this));
        }
        List<T> result = new ArrayList<T>();
        DefaultLookupContext context = new DefaultLookupContext();
        allServices.getAll(context, serviceType, result);
        return result;
    }

    public <T> T get(Class<T> serviceType) throws UnknownServiceException, ServiceLookupException {
        return serviceType.cast(doGet(serviceType));
    }

    public Object get(Type serviceType) throws UnknownServiceException, ServiceLookupException {
        return doGet(serviceType);
    }

    private Object doGet(Type serviceType) throws IllegalArgumentException {
        if (closed) {
            throw new IllegalStateException(String.format("Cannot locate service of type %s, as %s has been closed.", format(serviceType), this));
        }

        DefaultLookupContext context = new DefaultLookupContext();
        Object t = context.find(serviceType, allServices);
        if (t != null) {
            return t;
        }

        throw new UnknownServiceException(serviceType, String.format("No service of type %s available in %s.", format(serviceType), this));
    }

    public <T> Factory<T> getFactory(Class<T> type) {
        if (closed) {
            throw new IllegalStateException(String.format("Cannot locate factory for objects of type %s, as %s has been closed.", format(type), this));
        }

        DefaultLookupContext context = new DefaultLookupContext();
        Factory<T> factory = allServices.getFactory(context, type);
        if (factory != null) {
            return factory;
        }

        throw new UnknownServiceException(type, String.format("No factory for objects of type %s available in %s.", format(type), this));
    }

    public <T> T newInstance(Class<T> type) {
        return getFactory(type).create();
    }

    private static Object invoke(Method method, Object target, Object... args) {
        try {
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw UncheckedException.throwAsUncheckedException(e.getCause());
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    interface Provider extends Stoppable {
        /**
         * Locates a service instance of the given type. Returns null if this provider does not provide a service of this type.
         */
        <T> T getService(LookupContext context, Class<T> serviceType);

        /**
         * Locates a factory for services of the given type. Returns null if this provider does not provide any services of this type.
         */
        <T> Factory<T> getFactory(LookupContext context, Class<T> type);

        <T> void getAll(LookupContext context, Class<T> serviceType, List<T> result);
    }

    private class OwnServices implements Provider {
        private final List<Provider> providers = new ArrayList<Provider>();

        public <T> Factory<T> getFactory(LookupContext context, Class<T> type) {
            Factory<T> match = null;
            for (Provider provider : providers) {
                Factory<T> factory = provider.getFactory(context, type);
                if (factory != null) {
                    if (match != null) {
                        throw new ServiceLookupException(String.format("Multiple factories for objects of type %s available in %s.", format(type), DefaultServiceRegistry.this.toString()));
                    }
                    match = factory;
                }
            }
            return match;
        }

        public <T> T getService(LookupContext context, Class<T> serviceType) {
            T match = null;
            for (Provider provider : providers) {
                T service = provider.getService(context, serviceType);
                if (service != null) {
                    if (match != null) {
                        throw new ServiceLookupException(String.format("Multiple services of type %s available in %s.", format(serviceType), DefaultServiceRegistry.this.toString()));
                    }
                    match = service;
                }
            }
            return match;
        }

        public <T> void getAll(LookupContext context, Class<T> serviceType, List<T> result) {
            for (Provider provider : providers) {
                provider.getAll(context, serviceType, result);
            }
        }

        public void stop() {
            CompositeStoppable.stoppable(providers).stop();
        }

        public void add(Provider provider) {
            this.providers.add(provider);
        }
    }

    private static abstract class ManagedObjectProvider<T> implements Provider {
        private T instance;

        protected void setInstance(T instance) {
            this.instance = instance;
        }

        public T getInstance(LookupContext context) {
            if (instance == null) {
                instance = create(context);
                assert instance != null : String.format("create() of %s returned null", toString());
            }
            return instance;
        }

        protected abstract T create(LookupContext context);

        public void stop() {
            try {
                CompositeStoppable.stoppable(instance).stop();
            } finally {
                instance = null;
            }
        }
    }

    private static abstract class SingletonService extends ManagedObjectProvider<Object> {
        final Type serviceType;
        final Class serviceClass;

        SingletonService(Type serviceType) {
            this.serviceType = serviceType;
            serviceClass = toClass(serviceType);
        }

        @Override
        public String toString() {
            return String.format("Service %s", serviceType);
        }

        public <T> T getService(LookupContext context, Class<T> serviceType) {
            if (!serviceType.isAssignableFrom(this.serviceClass)) {
                return null;
            }
            return serviceType.cast(getInstance(context));
        }

        public <T> void getAll(LookupContext context, Class<T> serviceType, List<T> result) {
            if (serviceType.isAssignableFrom(this.serviceClass)) {
                result.add(serviceType.cast(getInstance(context)));
            }
        }

        public <T> Factory<T> getFactory(LookupContext context, Class<T> elementType) {
            if (!Factory.class.isAssignableFrom(serviceClass)) {
                return null;
            }
            return getFactory(context, serviceType, elementType);
        }

        private <T> Factory<T> getFactory(LookupContext context, Type type, Class<T> elementType) {
            Class c = toClass(type);
            if (!Factory.class.isAssignableFrom(c)) {
                return null;
            }

            if (type instanceof ParameterizedType) {
                // Check if type is Factory<? extends ElementType>
                ParameterizedType parameterizedType = (ParameterizedType) type;
                if (parameterizedType.getRawType().equals(Factory.class)) {
                    Type actualType = parameterizedType.getActualTypeArguments()[0];
                    if (actualType instanceof Class<?> && elementType.isAssignableFrom((Class<?>) actualType)) {
                        @SuppressWarnings("unchecked")
                        Factory<T> f = getService(context, Factory.class);
                        return f;
                    }
                }
            }

            // Check if type extends Factory<? extends ElementType>
            for (Type interfaceType : c.getGenericInterfaces()) {
                Factory<T> f = getFactory(context, interfaceType, elementType);
                if (f != null) {
                    return f;
                }
            }

            return null;
        }

        private Class toClass(Type type) {
            if (type instanceof Class) {
                return (Class) type;
            } else {
                ParameterizedType parameterizedType = (ParameterizedType) type;
                return (Class) parameterizedType.getRawType();
            }
        }
    }

    private class FactoryMethodService extends SingletonService {
        private final Object target;
        private final Method method;

        public FactoryMethodService(Object target, Method method) {
            super(method.getGenericReturnType());
            this.target = target;
            this.method = method;
        }

        @Override
        protected Object create(LookupContext context) {
            Object[] params = new Object[method.getParameterTypes().length];
            for (int i = 0; i < method.getGenericParameterTypes().length; i++) {
                Type paramType = method.getGenericParameterTypes()[i];
                try {
                    params[i] = paramType.equals(ServiceRegistry.class) ? DefaultServiceRegistry.this : context.find(paramType, allServices);
                } catch (ServiceDependencyCycle e) {
                    throw new ServiceLookupException(String.format("Cannot create service of type %s using %s.%s() as there is a cycle in its dependencies.",
                            format(method.getGenericReturnType()),
                            method.getDeclaringClass().getSimpleName(),
                            method.getName()), e);
                }
                if (params[i] == null) {
                    throw new ServiceLookupException(String.format("Cannot create service of type %s using %s.%s() as required service of type %s is not available.",
                            format(method.getGenericReturnType()),
                            method.getDeclaringClass().getSimpleName(),
                            method.getName(),
                            format(paramType)));

                }
            }
            Object result;
            try {
                result = invoke(method, target, params);
            } catch (Exception e) {
                throw new ServiceLookupException(String.format("Could not create service of type %s using %s.%s().",
                        format(method.getGenericReturnType()),
                        method.getDeclaringClass().getSimpleName(),
                        method.getName()),
                        e);
            }
            if (result == null) {
                throw new ServiceLookupException(String.format("Could not create service of type %s using %s.%s() as this method returned null.",
                        format(method.getGenericReturnType()),
                        method.getDeclaringClass().getSimpleName(),
                        method.getName()));
            }
            return result;
        }
    }

    private static class FixedInstanceService<T> extends SingletonService {
        public FixedInstanceService(Class<T> serviceType, T serviceInstance) {
            super(serviceType);
            setInstance(serviceInstance);
        }

        @Override
        protected Object create(LookupContext context) {
            throw new UnsupportedOperationException();
        }
    }

    private class DecoratorMethodService extends SingletonService {
        private final Object target;
        private final Method method;

        public DecoratorMethodService(Object target, Method method) {
            super(method.getGenericReturnType());
            this.target = target;
            this.method = method;
        }

        @Override
        protected Object create(LookupContext context) {
            Type paramType = method.getGenericParameterTypes()[0];
            Object value = new DefaultLookupContext().find(paramType, parentServices);
            if (value == null) {
                throw new ServiceLookupException(String.format("Cannot create service of type %s using %s.%s() as required service of type %s is not available in parent registries.",
                        format(method.getGenericReturnType()),
                        method.getDeclaringClass().getSimpleName(),
                        method.getName(),
                        format(paramType)));
            }
            Object result;
            try {
                result = invoke(method, target, value);
            } catch (Exception e) {
                throw new ServiceLookupException(String.format("Could not create service of type %s using %s.%s().",
                        format(method.getGenericReturnType()),
                        method.getDeclaringClass().getSimpleName(),
                        method.getName()),
                        e);
            }
            if (result == null) {
                throw new ServiceLookupException(String.format("Could not create service of type %s using %s.%s() as this method returned null.",
                        format(method.getGenericReturnType()),
                        method.getDeclaringClass().getSimpleName(),
                        method.getName()));
            }
            return result;
        }
    }

    private static class NestedServices extends ManagedObjectProvider<ServiceRegistry> {
        private final ServiceRegistry nested;

        public NestedServices(ServiceRegistry nested) {
            this.nested = nested;
            setInstance(nested);
        }

        @Override
        protected ServiceRegistry create(LookupContext context) {
            return nested;
        }

        public <T> Factory<T> getFactory(LookupContext context, Class<T> type) {
            try {
                Factory<T> factory = nested.getFactory(type);
                assert factory != null : String.format("nested registry returned null for factory type '%s'", type.getName());
                return factory;
            } catch (UnknownServiceException e) {
                if (e.getType().equals(type)) {
                    return null;
                }
                throw e;
            }
        }

        public <T> T getService(LookupContext context, Class<T> serviceType) {
            try {
                T service = nested.get(serviceType);
                assert service != null : String.format("nested registry returned null for service type '%s'", serviceType.getName());
                return service;
            } catch (UnknownServiceException e) {
                if (e.getType().equals(serviceType)) {
                    return null;
                }
                throw e;
            }
        }

        public <T> void getAll(LookupContext context, Class<T> serviceType, List<T> result) {
            result.addAll(nested.getAll(serviceType));
        }
    }

    private class CompositeProvider implements Provider {
        private final List<Provider> providers = new LinkedList<Provider>();

        public <T> T getService(LookupContext context, Class<T> serviceType) {
            for (Provider provider : providers) {
                T service = provider.getService(context, serviceType);
                if (service != null) {
                    return service;
                }
            }
            return null;
        }

        public <T> Factory<T> getFactory(LookupContext context, Class<T> type) {
            for (Provider provider : providers) {
                Factory<T> factory = provider.getFactory(context, type);
                if (factory != null) {
                    return factory;
                }
            }
            return null;
        }

        public <T> void getAll(LookupContext context, Class<T> serviceType, List<T> result) {
            for (Provider provider : providers) {
                provider.getAll(context, serviceType, result);
            }
        }

        public void stop() {
            try {
                CompositeStoppable.stoppable(providers).stop();
            } finally {
                providers.clear();
            }
        }
    }

    private class ParentServices implements Provider {
        private final ServiceRegistry parent;

        private ParentServices(ServiceRegistry parent) {
            this.parent = parent;
        }

        public <T> Factory<T> getFactory(LookupContext context, Class<T> type) {
            try {
                Factory<T> factory = parent.getFactory(type);
                assert factory != null : String.format("parent returned null for factory type '%s'", type.getName());
                return factory;
            } catch (UnknownServiceException e) {
                if (!e.getType().equals(type)) {
                    throw e;
                }
            }
            return null;
        }

        public <T> T getService(LookupContext context, Class<T> serviceType) {
            try {
                T service = parent.get(serviceType);
                assert service != null : String.format("parent returned null for service type '%s'", serviceType.getName());
                return service;
            } catch (UnknownServiceException e) {
                if (!e.getType().equals(serviceType)) {
                    throw e;
                }
            }
            return null;
        }

        public <T> void getAll(LookupContext context, Class<T> serviceType, List<T> result) {
            result.addAll(parent.getAll(serviceType));
        }

        public void stop() {
        }
    }

    interface LookupContext {
        @Nullable
        Object find(Type type, Provider provider);
    }

    private static class ServiceDependencyCycle extends RuntimeException {
        public ServiceDependencyCycle(String message) {
            super(message);
        }
    }

    private static class DefaultLookupContext implements LookupContext {
        private final Set<Type> visiting = new HashSet<Type>();

        public Object find(Type serviceType, Provider provider) {
            if (!visiting.add(serviceType)) {
                throw new ServiceDependencyCycle(String.format("Cycle in dependencies of service of type %s.", format(serviceType)));
            }
            try {
                if (serviceType instanceof ParameterizedType) {
                    ParameterizedType parameterizedType = (ParameterizedType) serviceType;
                    if (parameterizedType.getRawType().equals(Factory.class)) {
                        Type typeArg = parameterizedType.getActualTypeArguments()[0];
                        if (typeArg instanceof Class) {
                            return provider.getFactory(this, (Class) typeArg);
                        }
                        if (typeArg instanceof WildcardType) {
                            WildcardType wildcardType = (WildcardType) typeArg;
                            if (wildcardType.getLowerBounds().length == 1 && wildcardType.getUpperBounds().length == 1) {
                                if (wildcardType.getLowerBounds()[0] instanceof Class && wildcardType.getUpperBounds()[0].equals(Object.class)) {
                                    return provider.getFactory(this, (Class<Object>) wildcardType.getLowerBounds()[0]);
                                }
                            }
                            if (wildcardType.getLowerBounds().length == 0 && wildcardType.getUpperBounds().length == 1) {
                                if (wildcardType.getUpperBounds()[0] instanceof Class) {
                                    return provider.getFactory(this, (Class<Object>) wildcardType.getUpperBounds()[0]);
                                }
                            }
                        }
                    }
                } else if (serviceType instanceof Class) {
                    Class<?> serviceClass = (Class<?>) serviceType;
                    if (serviceType.equals(Factory.class)) {
                        throw new IllegalArgumentException("Cannot locate service of raw type Factory.");
                    }
                    if (serviceClass.isArray()) {
                        throw new IllegalArgumentException(String.format("Cannot locate service of array type %s[].", serviceClass.getComponentType().getSimpleName()));
                    }
                    if (serviceClass.isAnnotation()) {
                        throw new IllegalArgumentException(String.format("Cannot locate service of annotation type @%s.", serviceClass.getSimpleName()));
                    }
                    return provider.getService(this, serviceClass);
                }

                throw new UnsupportedOperationException(String.format("Cannot locate service of type %s yet.", format(serviceType)));
            } finally {
                visiting.remove(serviceType);
            }
        }
    }
}
