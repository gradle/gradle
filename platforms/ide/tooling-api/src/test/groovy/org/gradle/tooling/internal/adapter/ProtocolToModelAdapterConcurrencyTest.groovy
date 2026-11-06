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

package org.gradle.tooling.internal.adapter

import groovy.transform.CompileStatic
import spock.lang.Issue
import spock.lang.Specification

import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicIntegerArray

class ProtocolToModelAdapterConcurrencyTest extends Specification {
    final ProtocolToModelAdapter adapter = new ProtocolToModelAdapter()

    @Issue("https://github.com/gradle/gradle/issues/39657")
    def "view shared across threads returns source property values while another thread caches properties"() {
        def views = 2000
        def sources = (0..<views).collect { new TestWideModelImpl() }
        def pool = Executors.newFixedThreadPool(2)
        def failures = new ConcurrentLinkedQueue<String>()

        when:
        for (int round = 0; round < 20 && failures.isEmpty(); round++) {
            def graph = adapter.newGraph()
            List<TestWideModel> roundViews = sources.collect { graph.adapt(TestWideModel, it) }
            def phases = new AtomicIntegerArray(views)
            def reader = pool.submit { TestWideModelAccess.readWhileOtherThreadCaches(roundViews, phases, failures) }
            def writer = pool.submit { TestWideModelAccess.cacheMoreProperties(roundViews, phases, failures) }
            reader.get(1, TimeUnit.MINUTES)
            writer.get(1, TimeUnit.MINUTES)
        }

        then:
        failures.isEmpty()

        cleanup:
        pool?.shutdownNow()
    }

    @Issue("https://github.com/gradle/gradle/issues/39657")
    def "views shared across threads resolve the same source object to the same view"() {
        def parents = 2000
        def sources = (0..<parents).collect { new TestParentImpl() }
        def pool = Executors.newFixedThreadPool(2)
        List<Integer> mismatches = []

        when:
        for (int round = 0; round < 20 && mismatches.isEmpty(); round++) {
            def graph = adapter.newGraph()
            List<TestParent> views = sources.collect { graph.adapt(TestParent, it) }
            def arrived = new AtomicIntegerArray(parents)
            def viaA = pool.submit({ TestParentAccess.readChildren(views, arrived, true) } as Callable<List<TestChild>>)
            def viaB = pool.submit({ TestParentAccess.readChildren(views, arrived, false) } as Callable<List<TestChild>>)
            def childrenViaA = viaA.get(1, TimeUnit.MINUTES)
            def childrenViaB = viaB.get(1, TimeUnit.MINUTES)
            mismatches = (0..<parents).findAll { !childrenViaA[it].is(childrenViaB[it]) }
        }

        then:
        mismatches.isEmpty()

        cleanup:
        pool?.shutdownNow()
    }
}

interface TestWideModel {
    String getP0()

    String getP1()

    String getP2()

    String getP3()

    String getP4()

    String getP5()

    String getP6()

    String getP7()

    String getP8()

    String getP9()

    String getP10()

    String getP11()

    String getP12()

    String getP13()

    String getP14()

    String getP15()
}

class TestWideModelImpl {
    String getP0() {
        return "p0"
    }

    String getP1() {
        return "p1"
    }

    String getP2() {
        return "p2"
    }

    String getP3() {
        return "p3"
    }

    String getP4() {
        return "p4"
    }

    String getP5() {
        return "p5"
    }

    String getP6() {
        return "p6"
    }

    String getP7() {
        return "p7"
    }

    String getP8() {
        return "p8"
    }

    String getP9() {
        return "p9"
    }

    String getP10() {
        return "p10"
    }

    String getP11() {
        return "p11"
    }

    String getP12() {
        return "p12"
    }

    String getP13() {
        return "p13"
    }

    String getP14() {
        return "p14"
    }

    String getP15() {
        return "p15"
    }
}

@CompileStatic
class TestWideModelAccess {
    private static final int POPULATED = 1
    private static final int DONE = 2

    // Caches 12 properties, the most a default HashMap holds before resizing, then keeps reading them back until the other thread is done
    static void readWhileOtherThreadCaches(List<TestWideModel> views, AtomicIntegerArray phases, Collection<String> failures) {
        for (int i = 0; i < views.size(); i++) {
            TestWideModel view = views.get(i)
            checkFirst12Properties(view, i, failures)
            phases.set(i, POPULATED)
            long deadline = TestThreadHandshake.deadline()
            while (phases.get(i) != DONE) {
                checkFirst12Properties(view, i, failures)
                TestThreadHandshake.checkNotTimedOut(deadline, i)
            }
        }
    }

    // Caches the 13th+ properties, which resizes the property cache while the other thread reads from it
    static void cacheMoreProperties(List<TestWideModel> views, AtomicIntegerArray phases, Collection<String> failures) {
        for (int i = 0; i < views.size(); i++) {
            TestWideModel view = views.get(i)
            long deadline = TestThreadHandshake.deadline()
            while (phases.get(i) != POPULATED) {
                Thread.onSpinWait()
                TestThreadHandshake.checkNotTimedOut(deadline, i)
            }
            check(view.getP12(), "p12", i, failures)
            check(view.getP13(), "p13", i, failures)
            check(view.getP14(), "p14", i, failures)
            check(view.getP15(), "p15", i, failures)
            phases.set(i, DONE)
        }
    }

    private static void checkFirst12Properties(TestWideModel view, int index, Collection<String> failures) {
        check(view.getP0(), "p0", index, failures)
        check(view.getP1(), "p1", index, failures)
        check(view.getP2(), "p2", index, failures)
        check(view.getP3(), "p3", index, failures)
        check(view.getP4(), "p4", index, failures)
        check(view.getP5(), "p5", index, failures)
        check(view.getP6(), "p6", index, failures)
        check(view.getP7(), "p7", index, failures)
        check(view.getP8(), "p8", index, failures)
        check(view.getP9(), "p9", index, failures)
        check(view.getP10(), "p10", index, failures)
        check(view.getP11(), "p11", index, failures)
    }

    private static void check(String actual, String expected, int index, Collection<String> failures) {
        if (actual != expected) {
            failures.add("view " + index + ": expected " + expected + " but got " + actual)
        }
    }
}

interface TestParent {
    TestChild getA()

    TestChild getB()
}

interface TestChild {
    String getName()
}

class TestParentImpl {
    private final TestChildImpl child = new TestChildImpl()

    TestChildImpl getA() {
        return child
    }

    TestChildImpl getB() {
        return child
    }
}

class TestChildImpl {
    String getName() {
        return "child"
    }
}

@CompileStatic
class TestParentAccess {
    // Waits for the other thread at each parent, so that both threads adapt the shared child at the same time, one through getA() and the other through getB()
    static List<TestChild> readChildren(List<TestParent> parents, AtomicIntegerArray arrived, boolean viaA) {
        List<TestChild> children = new ArrayList<>(parents.size())
        for (int i = 0; i < parents.size(); i++) {
            TestParent parent = parents.get(i)
            arrived.incrementAndGet(i)
            long deadline = TestThreadHandshake.deadline()
            while (arrived.get(i) < 2) {
                Thread.onSpinWait()
                TestThreadHandshake.checkNotTimedOut(deadline, i)
            }
            children.add(viaA ? parent.getA() : parent.getB())
        }
        return children
    }
}

/**
 * Bounds the spin waits between the test threads, so that a failure in one thread fails the test instead of hanging it.
 */
@CompileStatic
class TestThreadHandshake {
    private static final long TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(30)

    static long deadline() {
        return System.nanoTime() + TIMEOUT_NANOS
    }

    static void checkNotTimedOut(long deadline, int index) {
        if (System.nanoTime() - deadline > 0) {
            throw new IllegalStateException("Timed out waiting for the other thread at view " + index)
        }
    }
}
