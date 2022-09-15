/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.junit

import junit.framework.TestCase
import junit.framework.TestSuite
import org.junit.Ignore
import org.junit.Test
import org.junit.internal.runners.InitializationError
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.internal.runners.JUnit4ClassRunner
import org.junit.internal.runners.SuiteMethod
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runner.Runner
import org.junit.runner.notification.RunNotifier
import spock.lang.Specification

import java.lang.annotation.IncompleteAnnotationException

/**
 * Tests {@link IgnoredTestDescriptorProvider}.
 */
class IgnoredTestDescriptorProviderTest extends Specification {

    def "can get individual test descriptions for ignored test classes"() {
        expect:
        describe(RunWithSpec.class)*.getDisplayName() == ["CHILD(SUITE)"]
        describe(SuiteMethodSpec.class)*.getDisplayName() == ["testSomething(org.gradle.api.internal.tasks.testing.junit.IgnoredTestDescriptorProviderTest\$TestCaseSpec)"]
        describe(TestCaseSpec.class)*.getDisplayName() == ["testSomething(org.gradle.api.internal.tasks.testing.junit.IgnoredTestDescriptorProviderTest\$TestCaseSpec)"]
        describe(JUnit4Spec.class)*.getDisplayName() == ["doTest(org.gradle.api.internal.tasks.testing.junit.IgnoredTestDescriptorProviderTest\$JUnit4Spec)"]
    }

    private List<Description> describe(Class<?> testClass) {
        IgnoredTestDescriptorProvider.getAllDescriptions(Description.createSuiteDescription(testClass), testClass.getName())
    }

    @SuppressWarnings
    def "can get @RunWith runner through legacy means"() {
        expect:
        IgnoredTestDescriptorProvider.getRunnerLegacy(RunWithSpec.class) instanceof CustomRunner

        when:
        IgnoredTestDescriptorProvider.getRunnerLegacy(EmptyRunWithSpec.class)

        then:
        thrown IncompleteAnnotationException

        when:
        IgnoredTestDescriptorProvider.getRunnerLegacy(MissingConstructorRunWithSpec.class)

        then:
        thrown InitializationError
    }

    def "can get SuiteMethod runner through legacy means"() {
        expect:
        IgnoredTestDescriptorProvider.getRunnerLegacy(SuiteMethodSpec.class) instanceof SuiteMethod
    }

    def "can get JUnit 3 runner through legacy means"() {
        expect:
        IgnoredTestDescriptorProvider.getRunnerLegacy(TestCaseSpec.class) instanceof JUnit38ClassRunner
    }

    def "can get JUnit 4 runner through legacy means"() {
        expect:
        IgnoredTestDescriptorProvider.getRunnerLegacy(JUnit4Spec.class) instanceof JUnit4ClassRunner
    }

    @Ignore
    @RunWith(CustomRunner.class)
    static class RunWithSpec {
        void doTest() {}
    }

    @Ignore
    @RunWith()
    static class EmptyRunWithSpec {
        void doTest() {}
    }

    @Ignore
    @RunWith(MissingConstructorRunner.class)
    static class MissingConstructorRunWithSpec {
        void doTest() {}
    }

    @Ignore
    static class SuiteMethodSpec {
        static TestSuite suite() {
            return new SuiteMethodSuite()
        }
        void doTest() {}
    }

    static class SuiteMethodSuite extends TestSuite {
        SuiteMethodSuite() {
            super(TestCaseSpec.class)
        }
    }

    @Ignore
    static class TestCaseSpec extends TestCase {
        void testSomething() {}
    }

    @Ignore
    static class JUnit4Spec {
        @Test
        void doTest() {}
    }

    static class CustomRunner extends Runner {
        CustomRunner(Class<?> clazz) {}

        @Override
        Description getDescription() {
            Description desc = Description.createSuiteDescription("SUITE")
            desc.addChild(Description.createTestDescription("SUITE", "CHILD"))
            return desc
        }

        @Override
        void run(RunNotifier notifier) {}
    }

    static class MissingConstructorRunner extends Runner {
        @Override
        Description getDescription() {
            return Description.createSuiteDescription("DESCRIPTION")
        }

        @Override
        void run(RunNotifier notifier) {}
    }
}
