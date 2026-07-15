/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.services.internal;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import kotlin.Unit;
import org.apache.commons.lang3.StringUtils;
import org.gradle.BuildAdapter;
import org.gradle.BuildResult;
import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectSet;
import org.gradle.api.NonExtensible;
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.internal.DelegatingNamedDomainObjectSet;
import org.gradle.api.internal.collections.DomainObjectCollectionFactory;
import org.gradle.api.internal.project.HoldsProjectState;
import org.gradle.api.provider.Provider;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.gradle.api.services.BuildServiceRegistration;
import org.gradle.api.services.BuildServiceSpec;
import org.gradle.internal.Cast;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.build.ExecutionResult;
import org.gradle.internal.buildtree.BuildModelParameters;
import org.gradle.internal.configuration.problems.IsolatedProjectsProblemsReporter;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.isolated.IsolationScheme;
import org.gradle.internal.isolation.IsolatableFactory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.resources.ResourceLock;
import org.gradle.internal.resources.SharedResource;
import org.gradle.internal.resources.SharedResourceLeaseRegistry;
import org.gradle.internal.service.ServiceRegistry;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.gradle.api.services.internal.BuildServiceProvider.asBuildServiceProvider;
import static org.gradle.internal.Cast.uncheckedCast;
import static org.gradle.internal.Cast.uncheckedNonnullCast;

public class DefaultBuildServicesRegistry implements BuildServiceRegistryInternal, HoldsProjectState {

    private final BuildIdentifier buildIdentifier;
    private final Lock registrationsLock = new ReentrantLock();
    /**
     * Registrations that have been claimed by some thread but not yet committed to {@link #internalRegistrations},
     * keyed by service name. Guarded by {@link #registrationsLock}.
     */
    @GuardedBy("registrationsLock")
    private final Map<String, RegistrationClaim> inFlightRegistrations = new HashMap<>();
    @GuardedBy("registrationsLock")
    private NamedDomainObjectSet<BuildServiceRegistration<?, ?>> internalRegistrations;
    @GuardedBy("registrationsLock")
    private IsolatedProjectsReportingRegistrationsContainer publicRegistrations;
    private final DomainObjectCollectionFactory collectionFactory;
    private final InstantiatorFactory instantiatorFactory;
    private final ServiceRegistry services;
    private final IsolatableFactory isolatableFactory;
    private final SharedResourceLeaseRegistry leaseRegistry;
    private final IsolationScheme<BuildService<?>, BuildServiceParameters> isolationScheme = new IsolationScheme<>(
        Cast.uncheckedCast(BuildService.class), BuildServiceParameters.class, BuildServiceParameters.None.class);
    private final Instantiator paramsInstantiator;
    private final Instantiator specInstantiator;
    private final BuildServiceProvider.Listener listener;
    private final IsolatedProjectsProblemsReporter problems;
    private final BuildModelParameters buildModelParameters;

    public DefaultBuildServicesRegistry(
        BuildIdentifier buildIdentifier,
        DomainObjectCollectionFactory collectionFactory,
        InstantiatorFactory instantiatorFactory,
        ServiceRegistry services,
        ListenerManager listenerManager,
        IsolatableFactory isolatableFactory,
        SharedResourceLeaseRegistry leaseRegistry,
        BuildServiceProvider.Listener listener,
        IsolatedProjectsProblemsReporter problems,
        BuildModelParameters buildModelParameters
    ) {
        this.buildIdentifier = buildIdentifier;
        this.internalRegistrations = uncheckedCast(collectionFactory.newNamedDomainObjectSet(BuildServiceRegistration.class));
        this.problems = problems;
        this.buildModelParameters = buildModelParameters;
        this.publicRegistrations = createPublicRegistrations(buildModelParameters, internalRegistrations, problems, registrationsLock, instantiatorFactory);
        this.collectionFactory = collectionFactory;
        this.instantiatorFactory = instantiatorFactory;
        this.services = services;
        this.isolatableFactory = isolatableFactory;
        this.leaseRegistry = leaseRegistry;
        this.paramsInstantiator = instantiatorFactory.decorateScheme().withServices(services).instantiator();
        this.specInstantiator = instantiatorFactory.decorateLenient(services);
        this.listener = listener;
        listenerManager.addListener(new ServiceCleanupListener());
    }

    /**
     * Runs the given function while holding {@link #registrationsLock}.
     *
     * <p>No user code may run inside the function: user code may grab arbitrary locks and cause a lock-order-inversion
     * deadlock with another thread that is registering or querying a build service (see gradle/gradle#36578).
     * A known exception is callbacks registered on the public registrations container (e.g. {@code getRegistrations().all { }}),
     * which are still fired by {@code registrations.add()} while the lock is held.
     */
    private <U extends @Nullable Object> U withRegistrations(Function<NamedDomainObjectSet<BuildServiceRegistration<?, ?>>, U> function) {
        registrationsLock.lock();
        try {
            return function.apply(internalRegistrations);
        } finally {
            registrationsLock.unlock();
        }
    }

    @Override
    public NamedDomainObjectSet<BuildServiceRegistration<?, ?>> getRegistrations() {
        registrationsLock.lock();
        try {
            return publicRegistrations;
        } finally {
            registrationsLock.unlock();
        }
    }

    private static IsolatedProjectsReportingRegistrationsContainer createPublicRegistrations(
        BuildModelParameters buildModelParameters,
        NamedDomainObjectSet<BuildServiceRegistration<?, ?>> internalRegistrations,
        IsolatedProjectsProblemsReporter problems,
        Lock registrationsLock,
        InstantiatorFactory instantiatorFactory
    ) {
        FunctionRunner synchronizedRunner = new FunctionRunner() {
            @Override
            public <P, R extends @Nullable Object> R run(P p, Function<P, R> function) {
                registrationsLock.lock();
                try {
                    return function.apply(p);
                } finally {
                    registrationsLock.unlock();
                }
            }
        };

        // Use instantiator so we generate Closure-accepting methods
        return instantiatorFactory.decorateScheme().instantiator().newInstance(
            IsolatedProjectsReportingRegistrationsContainer.class,
            internalRegistrations,
            problems,
            buildModelParameters,
            synchronizedRunner
        );
    }

    @Override
    @Nullable
    public SharedResource forService(BuildServiceProvider<?, ?> service) {
        DefaultServiceRegistration<?, ?> registration = findRegistration(service.getType(), service.getName());
        if (registration == null) {
            // no corresponding service registered
            return null;
        }
        return registration.asSharedResource(() -> {
            // Prevent further changes to registration
            registration.getMaxParallelUsages().finalizeValue();
            int maxUsages = registration.getMaxParallelUsages().getOrElse(-1);

            if (maxUsages > 0) {
                leaseRegistry.registerSharedResource(registration.getName(), maxUsages);
            }
            return new ServiceBackedSharedResource(registration.getName(), maxUsages, leaseRegistry);
        });
    }

    @Nullable
    @Override
    public DefaultServiceRegistration<?, ?> findRegistration(Class<?> type, String name) {
        return uncheckedCast(!name.isEmpty() ?
            findByName(name) :
            findByType(type)
        );
    }

    @Override
    public Set<BuildServiceRegistration<?, ?>> findRegistrations(Class<?> type, @Nullable String name) {
        return withRegistrations(registrations ->
            ImmutableSet.<BuildServiceRegistration<?, ?>>builder().addAll(registrations.matching(it ->
                type.isAssignableFrom(BuildServiceProvider.getProvidedType(it.getService()))
                    &&
                (StringUtils.isEmpty(name) || it.getName().equals(name))
            )).build()
        );
    }

    @Override
    @Nullable
    public BuildServiceRegistration<?, ?> findByName(String name) {
        return withRegistrations(registrations -> registrations.findByName(name));
    }

    @Nullable
    @Override
    public BuildServiceRegistration<?, ?> findByType(Class<?> type) {
        return findRegistrations(type, null).stream().findFirst().orElse(null);
    }

    @Override
    public <T extends BuildService<P>, P extends BuildServiceParameters> Provider<T> registerIfAbsent(String name, Class<T> implementationType, Action<? super BuildServiceSpec<P>> configureAction) {
        return doRegisterIfAbsent(name, implementationType, () -> {
            // TODO - extract some shared infrastructure to take care of parameter instantiation (eg strict vs lenient, which services are visible)
            P parameters = instantiateParametersOf(implementationType);

            // TODO - should defer execution of the action, to match behaviour for other container `register()` methods.
            DefaultServiceSpec<P> spec = uncheckedNonnullCast(specInstantiator.newInstance(DefaultServiceSpec.class, parameters));
            configureAction.execute(spec);
            return spec;
        });
    }

    @Override
    public BuildServiceProvider<?, ?> registerIfAbsent(String name, Class<? extends BuildService<?>> implementationType, BuildServiceParameters parameters, int maxUsages) {
        Supplier<BuildServiceSpec<?>> buildServiceSpecSupplier = () -> {
            DefaultServiceSpec<?> spec = uncheckedNonnullCast(specInstantiator.newInstance(DefaultServiceSpec.class, parameters));
            spec.getMaxParallelUsages().set(maxUsages);
            return spec;
        };
        return doRegisterIfAbsent(name, uncheckedNonnullCast(implementationType), uncheckedNonnullCast(buildServiceSpecSupplier));
    }

    private <T extends BuildService<P>, P extends BuildServiceParameters> BuildServiceProvider<T, P> doRegisterIfAbsent(String name, Class<T> implementationType, Supplier<BuildServiceSpec<P>> specSupplier) {
        while (true) {
            RegistrationClaim claim;
            boolean owning = false;
            registrationsLock.lock();
            try {
                BuildServiceRegistration<?, ?> existing = internalRegistrations.findByName(name);
                if (existing != null) {
                    // The service has been registered already.
                    // TODO - assert same type
                    // TODO - assert same parameters
                    return uncheckedNonnullCast(existing.getService());
                }
                claim = inFlightRegistrations.get(name);
                if (claim == null) {
                    // We're the first to register the service under that name.
                    // Mark our attempt so everyone else waits until we're done.
                    claim = new RegistrationClaim();
                    inFlightRegistrations.put(name, claim);
                    owning = true;
                }
            } finally {
                registrationsLock.unlock();
            }
            if (owning) {
                // We're the first thread to register the service under that name. Let's proceed.
                return configureAndCommit(name, implementationType, specSupplier, claim, true);
            }
            // Someone else is trying to register the service...
            if (claim.isOwnedByCurrentThread()) {
                // Re-entrance.
                // The configuration action of this registration is registering the same service again on the same thread.
                // The nested registration takes effect; when it completes, the outer invocation finds it committed
                // and returns it, discarding the outer spec.
                nagAboutReentrantRegistrationOf(name);
                return configureAndCommit(name, implementationType, specSupplier, claim, false);
            }
            // There is a concurrent registration going on. Let's wait for it to complete.
            BuildServiceProvider<?, ?> registeredByOtherThread = awaitRegistrationBy(claim);
            if (registeredByOtherThread != null) {
                // TODO - assert same type
                // TODO - assert same parameters
                return uncheckedNonnullCast(registeredByOtherThread);
            }
            // The claim owner failed and released the claim. Retry with our own configuration,
            // just like a sequential caller that follows a failed registration.
        }
    }

    /**
     * Waits for the registration claimed by another thread to complete, outside of any locks.
     *
     * @return the registered service provider, or {@code null} if the registering thread failed and the caller should retry
     */
    @Nullable
    private static BuildServiceProvider<?, ?> awaitRegistrationBy(RegistrationClaim claim) {
        try {
            return claim.result.get();
        } catch (InterruptedException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        } catch (ExecutionException e) {
            // It is acceptable to swallow the exception here.
            // The registering thread is going to see the original failure thrown from its registerIfAbsent.
            return null;
        }
    }

    /**
     * Configures the service spec, running user code without holding any locks, and commits the registration.
     * On failure, the claim is released if this invocation owns it, and the failure propagates to the caller.
     */
    private <T extends BuildService<P>, P extends BuildServiceParameters> BuildServiceProvider<T, P> configureAndCommit(
        String name,
        Class<T> implementationType,
        Supplier<BuildServiceSpec<P>> specSupplier,
        RegistrationClaim claim,
        boolean ownsClaim
    ) {
        try {
            // TODO - finalize the parameters during isolation
            // TODO - need to lock the project during isolation - should do this the same way as artifact transforms
            // Runs user code, so no locks may be held here.
            BuildServiceSpec<P> spec = specSupplier.get();
            return commitRegistration(name, implementationType, spec, claim);
        } catch (Throwable e) {
            if (ownsClaim) {
                releaseFailedClaim(name, claim, e);
            }
            // A nested (reentrant) invocation leaves the claim alone: the failure propagates into the configuration
            // action of the owning invocation, which may still recover and complete the registration.
            throw e;
        }
    }

    private <T extends BuildService<P>, P extends BuildServiceParameters> BuildServiceProvider<T, P> commitRegistration(
        String name,
        Class<T> implementationType,
        BuildServiceSpec<P> spec,
        RegistrationClaim claim
    ) {
        registrationsLock.lock();
        try {
            // Recheck: the service may have been committed by a reentrant nested registration, or by a racing register() call.
            BuildServiceRegistration<?, ?> existing = internalRegistrations.findByName(name);
            BuildServiceProvider<T, P> provider = existing != null
                ? uncheckedNonnullCast(existing.getService())
                : doRegister(name, implementationType, spec.getParameters(), spec.getMaxParallelUsages().getOrNull(), internalRegistrations);
            // No-ops if a reentrant nested registration already completed the claim.
            claim.result.complete(provider);
            inFlightRegistrations.remove(name, claim);
            return provider;
        } finally {
            registrationsLock.unlock();
        }
    }

    /**
     * Releases the claim of a registration whose configuration failed, so that waiting and future callers can retry.
     */
    private void releaseFailedClaim(String name, RegistrationClaim claim, Throwable failure) {
        registrationsLock.lock();
        try {
            inFlightRegistrations.remove(name, claim);
        } finally {
            registrationsLock.unlock();
        }
        // No-ops if a reentrant nested registration already completed the claim; the registration exists then,
        // and only the caller that failed sees the failure.
        claim.result.completeExceptionally(failure);
    }

    private static void nagAboutReentrantRegistrationOf(String name) {
        DeprecationLogger.deprecateBehaviour(String.format("Registering build service '%s' from the configuration action of its own registration.", name))
            .withAdvice("Register the service once, outside of its own configuration action.")
            .willBecomeAnErrorInGradle10()
            .withUpgradeGuideSection(9, "deprecated-reentrant-build-service-registration")
            .nagUser();
    }

    /**
     * An in-flight registration of a build service. The thread that created the claim runs the configuration action
     * without holding any locks and then commits the registration, completing {@link #result}. Other threads
     * trying to register a service with the same name wait for the result instead of registering.
     */
    private static class RegistrationClaim {
        private final Thread owner = Thread.currentThread();
        final CompletableFuture<BuildServiceProvider<?, ?>> result = new CompletableFuture<>();

        boolean isOwnedByCurrentThread() {
            return owner == Thread.currentThread();
        }
    }

    @Override
    public List<ResourceLock> getSharedResources(Set<Provider<? extends BuildService<?>>> services) {
        if (services.isEmpty()) {
            return Collections.emptyList();
        }
        ImmutableList.Builder<ResourceLock> locks = ImmutableList.builder();
        for (Provider<? extends BuildService<?>> service : services) {
            if (!service.isPresent()) {
                continue;
            }
            SharedResource resource = forService(asBuildServiceProvider(service));
            if (resource != null && resource.getMaxUsages() > 0) {
                locks.add(resource.getResourceLock());
            }
        }
        return locks.build();
    }

    private <T extends BuildService<P>, P extends BuildServiceParameters> P instantiateParametersOf(Class<T> implementationType) {
        Class<P> parameterType = isolationScheme.parameterTypeFor(implementationType);
        return isolationScheme.instantiateParameters(parameterType, paramsInstantiator::newInstance);
    }

    @Override
    public BuildServiceProvider<?, ?> register(String name, Class<? extends BuildService<?>> implementationType, BuildServiceParameters parameters, int maxUsages) {
        return withRegistrations(registrations -> {
            DefaultServiceRegistration<?, ?> registration = Cast.uncheckedCast(registrations.findByName(name));
            if (registration != null) {
                if (registration.provider.isKeepAlive()) {
                    // Reuse the service instance
                    return registration.provider;
                }
                throw new IllegalArgumentException(String.format("Service '%s' has already been registered.", name));
            }
            return doRegister(name, uncheckedNonnullCast(implementationType), parameters, maxUsages <= 0 ? null : maxUsages, registrations);
        });
    }

    @Override
    public BuildServiceProvider<?, ?> consume(String name, Class<? extends BuildService<?>> implementationType) {
        return doConsume(name, uncheckedCast(implementationType));
    }

    private <T extends BuildService<BuildServiceParameters>> BuildServiceProvider<T, BuildServiceParameters> doConsume(String name, Class<T> implementationType) {
        return new ConsumedBuildServiceProvider<>(buildIdentifier, name, implementationType, services);
    }

    private <T extends BuildService<P>, P extends BuildServiceParameters> BuildServiceProvider<T, P> doRegister(
        String name,
        Class<T> implementationType,
        P parameters,
        @Nullable Integer maxParallelUsages,
        NamedDomainObjectSet<BuildServiceRegistration<?, ?>> registrations
    ) {
        RegisteredBuildServiceProvider<T, P> provider = new RegisteredBuildServiceProvider<>(
            buildIdentifier,
            name,
            implementationType,
            parameters,
            isolationScheme,
            instantiatorFactory.injectScheme(),
            isolatableFactory,
            services,
            listener,
            maxParallelUsages
        );

        DefaultServiceRegistration<T, P> registration = uncheckedNonnullCast(specInstantiator.newInstance(DefaultServiceRegistration.class, name, parameters, provider));
        registration.getMaxParallelUsages().set(maxParallelUsages);
        registrations.add(registration);

        // TODO - should stop the service after last usage (ie after the last task that uses it) instead of at the end of the build
        // TODO - should reuse service across build invocations, until the parameters change (which contradicts the previous item)
        return provider;
    }

    @Override
    public void discardAll() {
        discardAll(false);
    }

    private void discardAll(boolean forceAll) {
        registrationsLock.lock();
        try {
            List<DefaultServiceRegistration<?, ?>> preserved = new ArrayList<>();
            try {
                ExecutionResult.forEach(internalRegistrations, registration -> {
                    DefaultServiceRegistration<?, ?> serviceRegistration = (DefaultServiceRegistration<?, ?>) registration;
                    // Do not stop services that are to be retained beyond configuration time (e.g. build event listeners)
                    if (forceAll || !serviceRegistration.provider.isKeepAlive()) {
                        serviceRegistration.provider.maybeStop();
                    } else {
                        preserved.add(serviceRegistration);
                    }
                }).rethrow();
            } finally {
                // Replace the entire container, rather than clear it, to discard all the service instances and because it may contain configuration actions and
                // other state that can affect the service instances when they are registered again
                internalRegistrations = Cast.uncheckedCast(collectionFactory.newNamedDomainObjectSet(BuildServiceRegistration.class));
                publicRegistrations = createPublicRegistrations(buildModelParameters, internalRegistrations, problems, registrationsLock, instantiatorFactory);
            }
            internalRegistrations.addAll(preserved);
        } finally {
            registrationsLock.unlock();
        }
    }

    private static class ServiceBackedSharedResource implements SharedResource {
        private final String name;
        private final int maxUsages;
        private final SharedResourceLeaseRegistry leaseRegistry;

        public ServiceBackedSharedResource(String name, int maxUsages, SharedResourceLeaseRegistry leaseRegistry) {
            this.name = name;
            this.maxUsages = maxUsages;
            this.leaseRegistry = leaseRegistry;
        }

        @Override
        public int getMaxUsages() {
            return maxUsages;
        }

        @Override
        public ResourceLock getResourceLock() {
            return leaseRegistry.getResourceLock(name);
        }
    }

    public static abstract class DefaultServiceRegistration<T extends BuildService<P>, P extends BuildServiceParameters> implements BuildServiceRegistration<T, P> {
        private final String name;
        private final P parameters;
        private final RegisteredBuildServiceProvider<T, P> provider;
        private SharedResource resourceWrapper;

        public DefaultServiceRegistration(String name, P parameters, RegisteredBuildServiceProvider<T, P> provider) {
            this.name = name;
            this.parameters = parameters;
            this.provider = provider;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public P getParameters() {
            return parameters;
        }

        @Override
        public Provider<T> getService() {
            return provider;
        }

        public SharedResource asSharedResource(Supplier<SharedResource> factory) {
            if (resourceWrapper == null) {
                resourceWrapper = factory.get();
            }
            return resourceWrapper;
        }
    }

    @NonExtensible
    public abstract static class DefaultServiceSpec<P extends BuildServiceParameters> implements BuildServiceSpec<P> {
        private final P parameters;

        public DefaultServiceSpec(P parameters) {
            this.parameters = parameters;
        }

        @Override
        public P getParameters() {
            return parameters;
        }

        @Override
        public void parameters(Action<? super P> configureAction) {
            configureAction.execute(parameters);
        }
    }

    private class ServiceCleanupListener extends BuildAdapter {
        @SuppressWarnings("deprecation")
        @Override
        public void buildFinished(BuildResult result) {
            discardAll(true);
        }
    }

    // package-private to permit instantiation
    static class IsolatedProjectsReportingRegistrationsContainer extends DelegatingNamedDomainObjectSet<BuildServiceRegistration<?, ?>> {

        private final IsolatedProjectsProblemsReporter problems;
        private final BuildModelParameters buildModelParameters;
        private final FunctionRunner synchronizedRunner;

        @Inject
        public IsolatedProjectsReportingRegistrationsContainer(
            NamedDomainObjectSet<BuildServiceRegistration<?, ?>> delegate,
            IsolatedProjectsProblemsReporter problems,
            BuildModelParameters buildModelParameters,
            FunctionRunner synchronizedRunner
        ) {
            super(delegate);
            this.problems = problems;
            this.buildModelParameters = buildModelParameters;
            this.synchronizedRunner = synchronizedRunner;
        }

        @Override
        public @Nullable BuildServiceRegistration<?, ?> findByName(String name) {
            // Do not call super.findByName so it does not call onMethodCall,
            // and we can unconditionally emit a violation below.
            return synchronizedRunner.run(name, getDelegate()::findByName);
        }

        @Override
        protected void onMethodCall(String signature) {
            if (buildModelParameters.isIsolatedProjects()) {
                problems.report(factory ->
                    factory.problem(null, messageBuilder -> {
                        messageBuilder.text(
                            "Cannot call '" + signature + "' on BuildServicesRegistry.getRegistrations() when Isolated Projects is enabled. " +
                                "Only 'findByName(String)' is permitted. " +
                                "Alternatively, use BuildServicesRegistry.registerIfAbsent(String, Class) if possible."
                        );
                        return Unit.INSTANCE;
                    }).exception().build()
                );
            }
            super.onMethodCall(signature);
        }

    }

    interface FunctionRunner {

        <P, R extends @Nullable Object> R run(P p, Function<P, R> function);

    }

}
