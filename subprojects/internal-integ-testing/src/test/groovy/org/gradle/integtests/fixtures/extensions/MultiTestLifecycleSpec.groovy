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

package org.gradle.integtests.fixtures.extensions


import org.gradle.integtests.fixtures.RequiredFeature
import org.junit.Assume
import org.junit.Rule
import org.junit.rules.ExternalResource
import spock.lang.Specification

@FluidDependenciesResolveTest
class MultiTestLifecycleSpec extends Specification {

    private static final Lifecycle LIFECYCLE = new Lifecycle()

    @Rule
    public final SampleRule rule = new SampleRule("myName")

    def setupSpec() {
        LIFECYCLE.pushEvent("setup spec")
    }

    def cleanupSpec() {
        LIFECYCLE.assertEvents([
            "setup spec",
            "rule before", "setup", "simple test: isFluid: false", "cleanup", "rule after",
            "rule before", "setup", "simple test: isFluid: true", "cleanup", "rule after",
            "rule before", "setup", "unrolled test: 1: isFluid: false", "cleanup", "rule after",
            "rule before", "setup", "unrolled test: 1: isFluid: true", "cleanup", "rule after",
            "rule before", "setup", "unrolled test: 2: isFluid: false", "cleanup", "rule after",
            "rule before", "setup", "unrolled test: 2: isFluid: true", "cleanup", "rule after",
            "rule before", "setup", "unrolled test: 3: isFluid: false", "cleanup", "rule after",
            "rule before", "setup", "unrolled test: 3: isFluid: true", "cleanup", "rule after",
            "rule before", "setup", "unrolled test with required: 1: isFluid: true", "cleanup", "rule after",
            "rule before", "setup", "unrolled test with required: 2: isFluid: true", "cleanup", "rule after",
            "rule before", "setup", "unrolled test with required: 3: isFluid: true", "cleanup", "rule after",
            "rule before", "setup", "skipped test: isFluid: false", "cleanup", "rule after",
            "rule before", "setup", "skipped test: isFluid: true", "cleanup", "rule after"
        ])
    }

    def setup() {
        LIFECYCLE.pushEvent("setup")
    }

    def cleanup() {
        LIFECYCLE.pushEvent("cleanup")
    }

    def simple() {
        LIFECYCLE.pushEvent("simple test: isFluid: ${FluidDependenciesResolveInterceptor.isFluid()}")
        expect:
        true
    }

    def "unrolled foo: #foo"() {
        LIFECYCLE.pushEvent("unrolled test: $foo: isFluid: ${FluidDependenciesResolveInterceptor.isFluid()}")
        expect:
        true

        where:
        foo << [1, 2, 3]
    }

    @RequiredFeature(feature = FluidDependenciesResolveInterceptor.ASSUME_FLUID_DEPENDENCIES, value = "true")
    def "unrolled foo with required: #foo"() {
        LIFECYCLE.pushEvent("unrolled test with required: $foo: isFluid: ${FluidDependenciesResolveInterceptor.isFluid()}")
        expect:
        true

        where:
        foo << [1, 2, 3]
    }

    def "skipped test"() {
        LIFECYCLE.pushEvent("skipped test: isFluid: ${FluidDependenciesResolveInterceptor.isFluid()}")
        Assume.assumeTrue("can skip test", false)

        expect:
        false
    }

    static class SampleRule extends ExternalResource {
        private final String name

        // We add a constructor parameter here, so this class can't be instantiated by the default constructor.
        // This way we can test if the class has been initialized correctly by Spock.
        SampleRule(String name) {
            this.name = name
        }

        @Override
        protected void before() throws Throwable {
            LIFECYCLE.pushEvent("rule before")
        }

        @Override
        protected void after() {
            LIFECYCLE.pushEvent("rule after")
        }
    }
}

class Lifecycle {
    private final List<String> events = new ArrayList<>()

    void pushEvent(String event) {
        println(event)
        events.add(event)
    }

    void assertEvents(List<String> expectedEvents) {
        assert expectedEvents == events
    }
}

