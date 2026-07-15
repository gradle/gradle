/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.services.internal

import org.gradle.api.artifacts.component.BuildIdentifier
import org.gradle.api.internal.collections.DomainObjectCollectionFactory
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.services.BuildServiceRegistry
import org.gradle.internal.Actions
import org.gradle.internal.buildtree.BuildModelParameters
import org.gradle.internal.configuration.problems.DefaultIsolatedProjectsProblemsReporter
import org.gradle.internal.configuration.problems.IsolatedProjectsProblemsListener
import org.gradle.internal.configuration.problems.ProblemFactory
import org.gradle.internal.deprecation.DeprecationLogger
import org.gradle.internal.event.DefaultListenerManager
import org.gradle.internal.instantiation.InstantiatorFactory
import org.gradle.internal.resources.SharedResourceLeaseRegistry
import org.gradle.internal.service.Provides
import org.gradle.internal.service.ServiceRegistrationProvider
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.snapshot.impl.DefaultIsolatableFactory
import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import org.gradle.util.TestUtil
import spock.lang.Timeout

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Concurrency tests for {@link DefaultBuildServicesRegistry}.
 *
 * <p>The registry must not hold any global lock while running the user-supplied configureAction
 * passed to {@code registerIfAbsent}, otherwise the user code can deadlock with another thread
 * that tries to register or query a build service. See gradle/gradle#36578.
 */
@SuppressWarnings("ConfigurationAvoidance")
class DefaultBuildServicesRegistryConcurrencyTest extends ConcurrentSpec {
    private static final int DEFAULT_TIMEOUT_SEC = 30

    def listenerManager = new DefaultListenerManager(Scope.Build)
    def isolatableFactory = new DefaultIsolatableFactory(null, TestUtil.managedFactoryRegistry())
    def leaseRegistry = Stub(SharedResourceLeaseRegistry)
    def buildIdentifier = Mock(BuildIdentifier)
    def ipProblemsReporter = new DefaultIsolatedProjectsProblemsReporter(
        Stub(ProblemFactory),
        Stub(IsolatedProjectsProblemsListener)
    )
    def buildModelParameters = Stub(BuildModelParameters)
    def services = TestUtil.createTestServices { registrations ->
        registrations.addProvider(new ServiceRegistrationProvider() {
            @Provides
            BuildServiceRegistry createBuildServiceRegistry() {
                return new DefaultBuildServicesRegistry(
                    buildIdentifier,
                    services.get(DomainObjectCollectionFactory),
                    services.get(InstantiatorFactory),
                    services,
                    listenerManager,
                    isolatableFactory,
                    leaseRegistry,
                    BuildServiceProvider.Listener.EMPTY,
                    ipProblemsReporter,
                    buildModelParameters
                )
            }
        })
    }
    def registry = services.get(BuildServiceRegistry)

    @Timeout(value = DEFAULT_TIMEOUT_SEC, unit = TimeUnit.SECONDS)
    def "configureAction does not deadlock when it waits on another thread registering a different service"() {
        given:
        def insideFirstConfigureAction = new CountDownLatch(1)
        def secondRegistrationDone = new CountDownLatch(1)

        when:
        async {
            // Thread A: registers "a"; its configureAction waits until thread B has registered "b".
            start {
                registry.registerIfAbsent("a", ServiceImpl) {
                    insideFirstConfigureAction.countDown()
                    assert secondRegistrationDone.await(20, TimeUnit.SECONDS): "thread B never finished registering 'b'"
                }
            }
            // Thread B: waits until thread A is inside its configureAction, then registers "b".
            start {
                assert insideFirstConfigureAction.await(20, TimeUnit.SECONDS): "thread A never entered its configureAction"
                registry.registerIfAbsent("b", ServiceImpl, Actions.doNothing())
                secondRegistrationDone.countDown()
            }
        }

        then:
        registry.registrations.findByName("a") != null
        registry.registrations.findByName("b") != null
    }

    @Timeout(value = DEFAULT_TIMEOUT_SEC, unit = TimeUnit.SECONDS)
    def "waits for a concurrent registration of the same service and runs the configuration action once"() {
        given:
        def actionRuns = new AtomicInteger()
        def providerA = null
        def providerB = null

        when:
        async {
            // Thread A: claims "a" and keeps configuring it while thread B enters registerIfAbsent for the same name.
            start {
                providerA = registry.registerIfAbsent("a", ServiceImpl) {
                    actionRuns.incrementAndGet()
                    instant.insideAction
                    thread.blockUntil.secondRegistering
                    // Give thread B some time to start waiting for the in-flight registration
                    thread.block()
                    instant.actionDone
                }
            }
            // Thread B: tries to register "a" while thread A is inside the configuration action.
            start {
                thread.blockUntil.insideAction
                instant.secondRegistering
                providerB = registry.registerIfAbsent("a", ServiceImpl) {
                    actionRuns.incrementAndGet()
                }
                instant.secondRegistered
            }
        }

        then:
        instant.secondRegistered > instant.actionDone
        actionRuns.get() == 1
        providerA.is(providerB)
        registry.registrations.findByName("a").service.is(providerA)
    }

    @Timeout(value = DEFAULT_TIMEOUT_SEC, unit = TimeUnit.SECONDS)
    def "waiter retries with its own configuration action when the concurrent registration fails"() {
        given:
        def actionRuns = new AtomicInteger()
        def firstFailure = null
        def providerB = null

        when:
        async {
            // Thread A: claims "a", lets thread B enter registerIfAbsent for the same name, then fails.
            start {
                try {
                    registry.registerIfAbsent("a", ServiceImpl) {
                        instant.insideAction
                        thread.blockUntil.secondRegistering
                        // Give thread B some time to start waiting for the in-flight registration
                        thread.block()
                        instant.aboutToFail
                        throw new RuntimeException("boom")
                    }
                } catch (RuntimeException e) {
                    firstFailure = e
                }
            }
            // Thread B: registers "a" with its own configuration action after the first registration fails.
            start {
                thread.blockUntil.insideAction
                instant.secondRegistering
                providerB = registry.registerIfAbsent("a", ServiceImpl) {
                    actionRuns.incrementAndGet()
                }
                instant.secondRegistered
            }
        }

        then:
        instant.secondRegistered > instant.aboutToFail
        firstFailure.message == "boom"
        actionRuns.get() == 1
        registry.registrations.findByName("a").service.is(providerB)
    }

    @SuppressWarnings('GrReassignedInClosureLocalVar')
    @Timeout(value = DEFAULT_TIMEOUT_SEC, unit = TimeUnit.SECONDS)
    def "findByName does not block while another thread runs a configuration action"() {
        given:
        def insideConfigureAction = new CountDownLatch(1)
        def queried = new CountDownLatch(1)
        def foundWhileInFlight = "sentinel"

        when:
        async {
            start {
                registry.registerIfAbsent("a", ServiceImpl) {
                    insideConfigureAction.countDown()
                    assert queried.await(20, TimeUnit.SECONDS): "the other thread never finished querying"
                }
            }
            start {
                assert insideConfigureAction.await(20, TimeUnit.SECONDS): "thread A never entered its configureAction"
                foundWhileInFlight = registry.registrations.findByName("a")
                queried.countDown()
            }
        }

        then:
        foundWhileInFlight == null
        registry.registrations.findByName("a") != null
    }

    @Timeout(value = DEFAULT_TIMEOUT_SEC, unit = TimeUnit.SECONDS)
    def "can register another service from a configuration action"() {
        when:
        def inner = null
        def outer = registry.registerIfAbsent("outer", ServiceImpl) {
            inner = registry.registerIfAbsent("inner", ServiceImpl, Actions.doNothing())
        }

        then:
        registry.registrations.findByName("outer").service.is(outer)
        registry.registrations.findByName("inner").service.is(inner)
    }

    @Timeout(value = DEFAULT_TIMEOUT_SEC, unit = TimeUnit.SECONDS)
    def "registering a service from its own configuration action yields the nested registration"() {
        when:
        def inner = null
        def outer = null
        // The nested registration is deprecated; the deprecation warning itself is covered by an integration test.
        DeprecationLogger.whileDisabled {
            outer = registry.registerIfAbsent("a", ServiceImpl) {
                inner = registry.registerIfAbsent("a", ServiceImpl, Actions.doNothing())
            }
        }

        then:
        outer.is(inner)
        registry.registrations.findByName("a").service.is(inner)
    }

    static abstract class ServiceImpl implements BuildService<BuildServiceParameters.None> {
    }
}
