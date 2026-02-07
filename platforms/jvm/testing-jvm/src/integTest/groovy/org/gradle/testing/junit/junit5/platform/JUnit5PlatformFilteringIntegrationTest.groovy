/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.testing.junit.junit5.platform

import org.gradle.api.JavaVersion
import org.gradle.testing.junit.platform.JUnitPlatformIntegrationSpec
import spock.lang.Issue

import static org.gradle.testing.fixture.JUnitCoverage.LATEST_ARCHUNIT_VERSION
import static org.gradle.testing.fixture.JUnitCoverage.getLATEST_JUNIT5_VERSION

class JUnit5PlatformFilteringIntegrationTest extends JUnitPlatformIntegrationSpec {
    @Override
    String getJupiterVersion() {
        return LATEST_JUNIT5_VERSION
    }

    /**
     * This test documents the status quo behavior of the test runner, where tests based on fields
     * are not filtered by exclude patterns.  It might be desirable to change this behavior in the
     * future to filter on field names directly; if this is done, this test should be replaced.
     */
    @Issue("https://github.com/gradle/gradle/issues/19352")
    def 'does not exclude tests with a non-standard test source if filter matches nothing'() {
        given:
        buildFile << """
            dependencies {
                testImplementation 'com.tngtech.archunit:archunit-junit5:${LATEST_ARCHUNIT_VERSION}'
            }

            test {
                filter {
                    excludeTestsMatching "*notMatchingAnythingSoEverythingShouldBeRun"
                }
            }
        """
        file('src/test/java/DeclaresTestsAsFieldsNotMethodsTest.java') << '''
            import com.tngtech.archunit.junit.AnalyzeClasses;
            import com.tngtech.archunit.junit.ArchTest;
            import com.tngtech.archunit.lang.ArchRule;

            import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

            @AnalyzeClasses(packages = "example")
            class DeclaresTestsAsFieldsNotMethodsTest {
                // this will create a JUnit Platform TestDescriptor with neither a Class- nor a MethodSource
                @ArchTest
                static final ArchRule example = classes().should().bePublic();
            }
        '''

        when:
        maybeExpectArchUnitUnsafeDeprecationWarning()
        succeeds('test')

        then:
        def results = resultsFor(testDirectory)
        results.testPath('DeclaresTestsAsFieldsNotMethodsTest').onlyRoot()
            .assertChildCount(1, 0)
    }

    /**
     * This test documents the status quo behavior of the test runner, where tests based on fields
     * are not filtered by exclude patterns.  It might be desirable to change this behavior in the
     * future to filter on field names directly; if this is done, this test should be replaced.
     */
    @Issue("https://github.com/gradle/gradle/issues/19352")
    def 'does not exclude tests with a non-standard test source if filter matches field name'() {
        given:
        buildFile << """
            dependencies {
                testImplementation 'com.tngtech.archunit:archunit-junit5:${LATEST_ARCHUNIT_VERSION}'
            }

            test {
                filter {
                    excludeTestsMatching "*example"
                }
            }
        """
        file('src/test/java/DeclaresTestsAsFieldsNotMethodsTest.java') << '''
            import com.tngtech.archunit.junit.AnalyzeClasses;
            import com.tngtech.archunit.junit.ArchTest;
            import com.tngtech.archunit.lang.ArchRule;

            import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

            @AnalyzeClasses(packages = "example")
            class DeclaresTestsAsFieldsNotMethodsTest {
                // this will create a JUnit Platform TestDescriptor with neither a Class- nor a MethodSource
                @ArchTest
                static final ArchRule example = classes().should().bePublic();
            }
        '''

        when:
        maybeExpectArchUnitUnsafeDeprecationWarning()
        succeeds('test')

        then:
        def results = resultsFor(testDirectory)
        results.testPath('DeclaresTestsAsFieldsNotMethodsTest').onlyRoot()
            .assertChildCount(1, 0)
    }

    /**
     * This test demonstrates the workaround for the inability to filter fields - we can
     * filter based on containing class name.
     */
    @Issue("https://github.com/gradle/gradle/issues/19352")
    def 'can filter tests with a non-standard test source using containing class name'() {
        given:
        buildFile << """
            dependencies {
                testImplementation 'com.tngtech.archunit:archunit-junit5:${LATEST_ARCHUNIT_VERSION}'
            }

            test {
                filter {
                    excludeTestsMatching "*DeclaresTestsAsFieldsNotMethodsTest"
                }
            }
        """
        file('src/test/java/DeclaresTestsAsFieldsNotMethodsTest.java') << '''
            import com.tngtech.archunit.junit.AnalyzeClasses;
            import com.tngtech.archunit.junit.ArchTest;
            import com.tngtech.archunit.lang.ArchRule;

            import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

            @AnalyzeClasses(packages = "example")
            class DeclaresTestsAsFieldsNotMethodsTest {
                // this will create a JUnit Platform TestDescriptor with neither a Class- nor a MethodSource
                @ArchTest
                static final ArchRule example = classes().should().bePublic();
            }
        '''

        expect:
        fails('test')
        errorOutput.contains("No tests found for given includes")
    }

    /**
     * ArchUnit uses an Guava version older than 33.4.5, which emits this warning when being used with Java 24+.
     */
    private void maybeExpectArchUnitUnsafeDeprecationWarning() {
        if (JavaVersion.current() >= JavaVersion.VERSION_24) {
            executer.expectExternalDeprecatedMessage("WARNING: A terminally deprecated method in sun.misc.Unsafe has been called")
        }
    }
}
