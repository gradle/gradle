/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.event

import org.gradle.internal.service.DefaultServiceRegistry
import org.gradle.internal.service.Provides
import org.gradle.internal.service.ServiceRegistrationProvider
import org.gradle.internal.service.scopes.EventScope
import org.gradle.internal.service.scopes.ListenerService
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.StatefulListener
import spock.lang.Specification

class DefaultListenerManagerInServiceRegistryTest extends Specification {
    def listenerManager = new DefaultListenerManager(Scope.BuildTree)
    def services = new DefaultServiceRegistry()

    def setup() {
        services.add(listenerManager)
    }

    def "automatically creates and registers stateful listener when first event is broadcast"() {
        def created = Mock(Runnable)
        def listener = Mock(TestListener)

        when:
        services.addProvider(new ServiceRegistrationProvider() {
            @Provides
            TestListener createListener() {
                created.run()
                return listener
            }
        })
        def broadcast = listenerManager.getBroadcaster(TestListener)

        then:
        0 * _

        when:
        broadcast.something("12")

        then:
        1 * created.run()
        1 * listener.something("12")
        0 * _
    }

    def "automatically registers stateful listener when first event is broadcast from child"() {
        def created = Mock(Runnable)
        def listener = Mock(TestListener)

        when:
        services.addProvider(new ServiceRegistrationProvider() {
            @Provides
            TestListener createListener() {
                created.run()
                return listener
            }
        })
        def broadcast = listenerManager.createChild(Scope.BuildTree).getBroadcaster(TestListener)

        then:
        0 * _

        when:
        broadcast.something("12")

        then:
        1 * created.run()
        1 * listener.something("12")
        0 * _
    }

    def "registers stateful listeners that have already been created prior to first event"() {
        def listener = Mock(TestListener)

        when:
        services.addProvider(new ServiceRegistrationProvider() {
            @Provides
            TestListener createListener() {
                return listener
            }
        })
        services.get(TestListener)
        def broadcast = listenerManager.getBroadcaster(TestListener)

        then:
        0 * _

        when:
        broadcast.something("12")

        then:
        1 * listener.something("12")
        0 * _
    }

    def "does not eagerly create listener manager"() {
        def created = Mock(Runnable)
        def listener = Mock(TestListener)
        def services = new DefaultServiceRegistry()

        when:
        services.addProvider(new ServiceRegistrationProvider() {
            @Provides
            DefaultListenerManager createListenerManager() {
                created.run()
                return listenerManager
            }
        })
        services.add(listener)

        then:
        0 * _

        when:
        services.get(ListenerManager)

        then:
        1 * created.run()
        0 * _
    }

    def "does not create stateful listeners of other types when event is fired"() {
        def listener = Mock(TestListener)

        when:
        services.addProvider(new ServiceRegistrationProvider() {
            @Provides
            DifferentListener createListener1() {
                throw new RuntimeException("should not happen")
            }

            @Provides
            TestListener createListener() {
                return listener
            }
        })
        def broadcast = listenerManager.getBroadcaster(TestListener)

        then:
        0 * _

        when:
        broadcast.something("12")

        then:
        1 * listener.something("12")
        0 * _
    }

    def "creates stateful listener registered after broadcaster is created"() {
        def created = Mock(Runnable)
        def listener = Mock(TestListener)

        when:
        def broadcast = listenerManager.getBroadcaster(TestListener)
        services.addProvider(new ServiceRegistrationProvider() {
            @Provides
            TestListener createListener() {
                created.run()
                return listener
            }
        })

        then:
        0 * _

        when:
        broadcast.something("12")

        then:
        1 * created.run()
        1 * listener.something("12")
        0 * _
    }

    def "creates stateful listener registered after another listener added"() {
        def created = Mock(Runnable)
        def listener = Mock(TestListener)

        when:
        listenerManager.addListener(Stub(TestListener))
        services.addProvider(new ServiceRegistrationProvider() {
            @Provides
            TestListener createListener() {
                created.run()
                return listener
            }
        })
        def broadcast = listenerManager.getBroadcaster(TestListener)

        then:
        0 * _

        when:
        broadcast.something("12")

        then:
        1 * created.run()
        1 * listener.something("12")
        0 * _
    }

    def "service can implement multiple stateful listener interfaces"() {
        def created = Mock(Runnable)
        def listener = Mock(MultipleListeners)

        when:
        services.addProvider(new ServiceRegistrationProvider() {
            @Provides
            MultipleListeners createListener() {
                created.run()
                return listener
            }
        })
        def broadcast1 = listenerManager.getBroadcaster(TestListener)

        then:
        0 * _

        when:
        broadcast1.something("12")

        then:
        1 * created.run()
        1 * listener.something("12")
        0 * _

        when:
        def broadcast2 = listenerManager.getBroadcaster(DifferentListener)
        broadcast2.somethingElse("11")

        then:
        1 * listener.somethingElse("11")
        0 * _
    }

    def "registers stateful listeners that are registered before listener manager"() {
        given:
        def created = Mock(Runnable)
        def listener = Mock(TestListener)
        def services = new DefaultServiceRegistry()

        when:
        services.addProvider(new ServiceRegistrationProvider() {
            @Provides
            TestListener createTestListener() {
                created.run()
                return listener
            }
        })
        services.addProvider(new ServiceRegistrationProvider() {
            @Provides
            DefaultListenerManager createListenerManager() {
                return listenerManager
            }
        })
        def broadcast = services.get(ListenerManager).getBroadcaster(TestListener)

        then:
        0 * _

        when:
        broadcast.something("12")

        then:
        1 * created.run()
        1 * listener.something("12")
        0 * _
    }

    def "fails when listener manager is not declared as annotation handler"() {
        given:
        def services = new DefaultServiceRegistry()

        when:
        services.add(ListenerManager, new DefaultListenerManager(Scope.BuildTree))

        then:
        def e = thrown(IllegalStateException)
        e.message == 'Service ListenerManager with implementation DefaultListenerManager implements AnnotatedServiceLifecycleHandler but is not declared as a service of this type. This service is declared as having type ListenerManager.'
    }

    def "fails when listener manager factory is not declared as annotation handler"() {
        given:
        def services = new DefaultServiceRegistry()
        services.addProvider(new ServiceRegistrationProvider() {
            @Provides
            ListenerManager createListenerManager() {
                return new DefaultListenerManager(Scope.BuildTree)
            }
        })

        when:
        services.get(ListenerManager.class)

        then:
        def e = thrown(IllegalStateException)
        e.message == 'Service ListenerManager at DefaultListenerManagerInServiceRegistryTest$.createListenerManager() implements AnnotatedServiceLifecycleHandler but is not declared as a service of this type. This service is declared as having type ListenerManager.'
    }

    def "fails when listener service is not declared as listener type"() {
        def listener = Mock(SubListener)
        services.addProvider(new ServiceRegistrationProvider() {
            @Provides
            Runnable createListener() {
                return listener
            }
        })

        when:
        services.get(Runnable)

        then:
        def e = thrown(IllegalStateException)
        e.message == 'Service Runnable at DefaultListenerManagerInServiceRegistryTest$.createListener() is annotated with @StatefulListener but is not declared as a service with this annotation. This service is declared as having type Runnable.'
    }

    def "fails when stateful listener registered after first event"() {
        def created = Mock(Runnable)
        def listener = Mock(TestListener)

        when:
        def broadcast = listenerManager.getBroadcaster(TestListener)
        broadcast.something("12")
        services.addProvider(new ServiceRegistrationProvider() {
            @Provides
            TestListener createListener() {
                created.run()
                return listener
            }
        })

        then:
        def e = thrown(IllegalStateException)
        e.message == "Cannot add listener of type TestListener after events have been broadcast."

        0 * _
    }

    def "automatically creates and registers listener service when any event is fired"() {
        def created = Mock(Runnable)
        def service = Mock(TestListenerService)

        when:
        services.addProvider(new ServiceRegistrationProvider() {
            @Provides
            TestListenerService createListener() {
                created.run()
                return service
            }
        })

        then:
        0 * _

        when:
        listenerManager.getBroadcaster(TestListener).something("12")

        then:
        1 * created.run()
        0 * _

        when:
        listenerManager.getBroadcaster(NotStatefulListener).something("12")

        then:
        1 * service.something("12")
        0 * _
    }

    def "listener service can take dependency that is registered later"() {
        def created = Mock(Runnable)
        def service = Mock(TestListenerService)

        when:
        services.addProvider(new ServiceRegistrationProvider() {
            @Provides
            TestListenerService createListener(Runnable action) {
                action.run()
                return service
            }
        })

        then:
        0 * _

        when:
        services.add(Runnable, created)
        listenerManager.getBroadcaster(NotStatefulListener).something("12")

        then:
        1 * created.run()
        1 * service.something("12")
        0 * _
    }

    @EventScope(Scope.BuildTree)
    interface NotStatefulListener {
        void something(String param)
    }

    @ListenerService
    abstract class TestListenerService implements NotStatefulListener {
    }

    @EventScope(Scope.BuildTree)
    @StatefulListener
    interface TestListener {
        void something(String param)
    }

    abstract static class SubListener implements TestListener, Runnable {
    }

    @EventScope(Scope.BuildTree)
    @StatefulListener
    interface DifferentListener {
        void somethingElse(String param)
    }

    interface MultipleListeners extends TestListener, DifferentListener {
    }
}
