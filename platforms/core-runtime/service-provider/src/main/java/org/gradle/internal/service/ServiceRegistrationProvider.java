/*
 * Copyright 2024 the original author or authors.
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

/**
 * Marker interface for reflection-based service declaration and registration.
 *
 * <h3>Declaring statically</h3>
 * You can declare services and factories by adding methods to the implementation of this interface.
 * <p>
 * The service-declaring methods are
 * <ul>
 * <li> not static and annotated with {@link Provides @Provides}
 * <li> have a name that starts with {@code create} or {@code decorate}
 * <li> have zero or more parameters declaring dependencies
 * <li> have a non-void return type
 * </ul>
 *
 * For example:
 * <pre><code class="language-java">
 * &#64;Provides
 * protected SomeService createSomeService(OtherService otherServiceDependency) { ... }</code></pre>
 *
 * <p>
 * Any other methods will not be ignored.
 * <p>
 * Factories are declared similarly by having the method return {@code Factory<SomeService>}.
 * The factories are used by {@link ServiceRegistry#getFactory(Class)} and {@link ServiceRegistry#newInstance(Class)} methods.
 *
 * <h3>Registering dynamically</h3>
 * You can register services dynamically by declaring a {@code configure} method with a {@link ServiceRegistration} parameter.
 * <p>
 * The recognized methods are:
 * <ul>
 * <li> not static and called {@code configure}
 * <li> have one of the parameters of type {@link ServiceRegistration}
 * <li> have zero or more parameters declaring dependencies
 * <li> have a void return type
 * </ul>
 *
 * For example:
 * <pre><code class="language-java">
 * protected void configure(ServiceRegistration registration) { ... }</code></pre>
 *
 * <p>
 * The {@code configure} methods can be injected with additional parameters the same way as service-declaring methods.
 *
 * <h3>Dependency injection</h3>
 *
 * Both service-declaring methods and {@code configure} methods can have parameters
 * that describe their dependencies.
 * <p>
 * On top of the basic case of injecting dependencies, more advanced use-cases are also supported:
 * decoration, aggregation, owner registry injection.
 * <pre><code class="language-java">
 * &#64;Provides
 * protected MyService createMyService(
 *     SomeService someService,
 *     MyService myServiceFromParent,
 *     List&lt;OtherService&gt; otherServices,
 *     ServiceRegistry ownerServiceRegistry
 * ) { ... }</code></pre>
 * <p>
 *
 * <b>Basic dependency.</b>
 * As long as the other service is available in the same or one of the parent registries,
 * it will be injected into the parameter. See {@code SomeService someService} in the example.
 * <p>
 * If the service is available in a service registry, the parent registries are not checked.
 * This can also be used to <b>override</b> services in child registries by providing a service of the same type.
 * <p>
 *
 * <b>Decoration.</b>
 * If {@code MyService} is available in a parent registry, then it can be decorated in child registries.
 * When the parameter has the same type as the service type (return-type),
 * the parameter is injected with an instance of the service from a parent registry.
 * See {@code MyService myServiceFromParent} in the example.
 * <p>
 *
 * <b>Aggregation.</b>
 * When the parameter is of type {@code List<T>}, it will receive with all services of type {@code T}
 * from the current and all parent registries.
 * If there are no services of this type, the list will be <em>empty</em>.
 * See <code>List&lt;OtherService&gt; otherServices</code> in the example.
 * <p>
 *
 * <b>Owner dependency.</b>
 * When the parameter is of type {@link ServiceRegistry}, it will receive an instance of registry that owns the service.
 * See {@code ServiceRegistry ownerServiceRegistry} in the example.
 *
 * <h3>Service lookup order</h3>
 *
 * <b>Own services</b> of a registry are services contributed by the service providers.
 * <p>
 * <b>All services</b> of a registry are its own services and <em>all services</em> of all its parents.
 * <p>
 * The lookup order for dependencies is the following:
 * <ol>
 * <li> Own services of the current registry
 * <li> All services of the first parent
 * <li> All services of the second parent
 * <li> ...
 * </ol>
 *
 * The <em>decorator</em> declarations skip the own services, and start the lookup in the parents.
 *
 * <h3>Service visibility</h3>
 *
 * By default, all registered services are visible to all consumers, both via injection and lookup.
 *
 * <h4>Private services</h4>
 *
 * Using {@link PrivateService} annotation the services can be made <em>private</em> to the registration provider that declares them.
 * <p>
 * A private service is visible only within the same <em>registration provider</em>.
 * It is not visible to other registration providers in the same registry or to other registries.
 * <p>
 * The lookup for private services will fail if no other service can fulfil the lookup request.
 * The private services are also not collected as part of the <em>aggregated</em> injection.
 *
 * <h3>Service lifetime</h3>
 *
 * Services are created lazily and might not be instantiated at all during the lifetime of the owning service registry.
 * <p>
 * If a service instance was created and the service implements {@link java.io.Closeable#close() Closeable} or
 * {@link org.gradle.internal.concurrent.Stoppable#stop() Stoppable} then the appropriate
 * method is called to dispose of it when the owning service registry is {@link CloseableServiceRegistry#close() closed}.
 */
public interface ServiceRegistrationProvider {
}
