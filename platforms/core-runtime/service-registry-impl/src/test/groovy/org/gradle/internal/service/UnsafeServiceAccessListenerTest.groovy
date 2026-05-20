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

package org.gradle.internal.service

import com.google.errorprone.annotations.ThreadSafe
import spock.lang.Specification

import java.lang.reflect.Type
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

class UnsafeServiceAccessListenerTest extends Specification {

    List<Class<?>> reportedTypes = []

    UnsafeServiceAccessListener listener = new UnsafeServiceAccessListener() {
        @Override
        void onUnsafeAccess(Class<?> serviceType) {
            synchronized (reportedTypes) {
                reportedTypes.add(serviceType)
            }
        }
    }

    def "get of @ThreadSafe type does not notify"() {
        given:
        def registry = checked(parentWith(ThreadSafeService, new ThreadSafeServiceImpl()))

        when:
        def result = registry.get(ThreadSafeService)

        then:
        result instanceof ThreadSafeServiceImpl
        reportedTypes == []
    }

    def "get of non-@ThreadSafe type notifies once and still returns the service"() {
        given:
        def registry = checked(parentWith(UnsafeService, new UnsafeServiceImpl()))

        when:
        def result = registry.get(UnsafeService)

        then:
        result instanceof UnsafeServiceImpl
        reportedTypes == [UnsafeService]
    }

    def "@ThreadSafe inherited from a superclass passes the check"() {
        given:
        def registry = checked(parentWith(SubclassOfThreadSafeBase, new SubclassOfThreadSafeBase()))

        when:
        registry.get(SubclassOfThreadSafeBase)

        then:
        reportedTypes == []
    }

    def "@ThreadSafe on a superinterface passes the check"() {
        given:
        def registry = checked(parentWith(ImplOfThreadSafeInterface, new ImplOfThreadSafeInterface()))

        when:
        registry.get(ImplOfThreadSafeInterface)

        then:
        reportedTypes == []
    }

    def "find for an existing non-@ThreadSafe service notifies"() {
        given:
        def registry = checked(parentWith(UnsafeService, new UnsafeServiceImpl()))

        when:
        def result = registry.find((Type) UnsafeService)

        then:
        result instanceof UnsafeServiceImpl
        reportedTypes == [UnsafeService]
    }

    def "find notifies based on the requested type even when the service is missing"() {
        given:
        def registry = checked(new DefaultServiceRegistry())

        when:
        def result = registry.find((Type) UnsafeService)

        then:
        result == null
        reportedTypes == [UnsafeService]
    }

    def "getAll of a non-@ThreadSafe type notifies once and returns the services"() {
        given:
        def a = new UnsafeServiceImpl()
        def parent = new DefaultServiceRegistry()
        parent.register { it.add(UnsafeService, a) }
        def registry = checked(parent)

        when:
        def result = registry.getAll(UnsafeService)

        then:
        result == [a]
        reportedTypes == [UnsafeService]
    }

    def "getAll notifies based on the requested type even when no services match"() {
        given:
        def registry = checked(new DefaultServiceRegistry())

        when:
        def result = registry.getAll(UnsafeService)

        then:
        result == []
        reportedTypes == [UnsafeService]
    }

    def "no listener means no check fires"() {
        given:
        def registry = new DefaultServiceRegistry("plain", parentWith(UnsafeService, new UnsafeServiceImpl()))

        when:
        registry.get(UnsafeService)

        then:
        reportedTypes == []
    }

    def "concurrent lookups for distinct types notify the expected number of times"() {
        given:
        def parent = new DefaultServiceRegistry()
        parent.register { reg ->
            reg.add(ThreadSafeServiceImpl, new ThreadSafeServiceImpl())
            reg.add(UnsafeServiceImpl, new UnsafeServiceImpl())
            reg.add(UnsafeOtherImpl, new UnsafeOtherImpl())
        }
        def registry = checked(parent)
        def threadCount = 30
        def latch = new CountDownLatch(1)
        def started = new AtomicInteger(0)

        when:
        def threads = (0..<threadCount).collect { i ->
            Thread.start {
                started.incrementAndGet()
                latch.await()
                switch (i % 3) {
                    case 0: registry.get(ThreadSafeServiceImpl); break
                    case 1: registry.get(UnsafeServiceImpl); break
                    case 2: registry.get(UnsafeOtherImpl); break
                }
            }
        }
        while (started.get() < threadCount) { Thread.sleep(1) }
        latch.countDown()
        threads*.join()

        then:
        reportedTypes.size() == 20
    }

    private DefaultServiceRegistry checked(ServiceRegistry parent) {
        return new DefaultServiceRegistry("test-checked", listener, parent)
    }

    private static ServiceRegistry parentWith(Class<?> serviceType, Object service) {
        def registry = new DefaultServiceRegistry()
        registry.register { it.add(serviceType, service) }
        return registry
    }

    @ThreadSafe
    static interface ThreadSafeService {}

    static class ThreadSafeServiceImpl implements ThreadSafeService {}

    static interface UnsafeService {}

    static class UnsafeServiceImpl implements UnsafeService {}

    static interface UnsafeOther {}

    static class UnsafeOtherImpl implements UnsafeOther {}

    @ThreadSafe
    static class ThreadSafeBase {}

    static class SubclassOfThreadSafeBase extends ThreadSafeBase {}

    @ThreadSafe
    static interface ThreadSafeInterface {}

    static interface IndirectInterface extends ThreadSafeInterface {}

    static class ImplOfThreadSafeInterface implements IndirectInterface {}
}
