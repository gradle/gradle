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

import org.gradle.testing.junit.platform.JUnitPlatformIntegrationSpec
import spock.lang.Issue

import static org.gradle.testing.fixture.JUnitCoverage.LATEST_ARCHUNIT_VERSION
import static org.gradle.testing.fixture.JUnitCoverage.getLATEST_JUNIT5_VERSION

/**
 * Filtering of tests that JUnit Platform engines declare on fields rather than methods, as ArchUnit does.
 */
class JUnit5PlatformFilteringIntegrationTest extends JUnitPlatformIntegrationSpec {
    @Override
    String getJupiterVersion() {
        return LATEST_JUNIT5_VERSION
    }

    def setup() {
        buildFile << """
            dependencies {
                testImplementation 'com.tngtech.archunit:archunit-junit5:${LATEST_ARCHUNIT_VERSION}'
            }
        """
        file('src/test/java/sample/ArchRulesTest.java') << '''
            package sample;

            import com.tngtech.archunit.junit.AnalyzeClasses;
            import com.tngtech.archunit.junit.ArchTest;
            import com.tngtech.archunit.junit.ArchTests;
            import com.tngtech.archunit.lang.ArchRule;

            import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

            @AnalyzeClasses(packages = "sample")
            public class ArchRulesTest {
                @ArchTest
                static final ArchRule firstRule = classes().should().bePublic();

                @ArchTest
                static final ArchRule secondRule = classes().should().bePublic();

                @ArchTest
                static final ArchTests nested = ArchTests.in(NestedRules.class);
            }
        '''
        file('src/test/java/sample/NestedRules.java') << '''
            package sample;

            import com.tngtech.archunit.junit.ArchTest;
            import com.tngtech.archunit.lang.ArchRule;

            import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

            public class NestedRules {
                @ArchTest
                static final ArchRule nestedRule = classes().should().bePublic();
            }
        '''
        file('src/test/java/sample/OtherArchRulesTest.java') << '''
            package sample;

            import com.tngtech.archunit.junit.AnalyzeClasses;
            import com.tngtech.archunit.junit.ArchTest;
            import com.tngtech.archunit.lang.ArchRule;

            import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

            @AnalyzeClasses(packages = "sample")
            public class OtherArchRulesTest {
                @ArchTest
                static final ArchRule otherRule = classes().should().bePublic();
            }
        '''
        file('src/test/java/sample/JupiterTest.java') << '''
            package sample;

            import org.junit.jupiter.api.Test;

            public class JupiterTest {
                @Test
                public void someMethod() {}

                @Test
                public void otherMethod() {}
            }
        '''
    }

    @Issue("https://github.com/gradle/gradle/issues/19352")
    def 'does not exclude tests declared as fields if filter matches nothing'() {
        given:
        buildFile << """
            test {
                filter {
                    excludeTestsMatching "*notMatchingAnythingSoEverythingShouldBeRun"
                }
            }
        """

        when:
        succeeds('test')

        then:
        resultsFor().assertTestPathsExecuted(
            ':sample.ArchRulesTest:firstRule',
            ':sample.ArchRulesTest:secondRule',
            ':sample.ArchRulesTest:NestedRules:NestedRules > nestedRule',
            ':sample.OtherArchRulesTest:otherRule',
            ':sample.JupiterTest:someMethod()',
            ':sample.JupiterTest:otherMethod()'
        )
    }

    def 'excludes tests declared as fields if filter matches field name'() {
        given:
        buildFile << """
            test {
                filter {
                    excludeTestsMatching "*firstRule"
                }
            }
        """

        when:
        succeeds('test')

        then:
        resultsFor().assertTestPathsExecuted(
            ':sample.ArchRulesTest:secondRule',
            ':sample.ArchRulesTest:NestedRules:NestedRules > nestedRule',
            ':sample.OtherArchRulesTest:otherRule',
            ':sample.JupiterTest:someMethod()',
            ':sample.JupiterTest:otherMethod()'
        )
    }

    @Issue("https://github.com/gradle/gradle/issues/19352")
    def 'excludes tests declared as fields if filter matches containing class name'() {
        given:
        buildFile << """
            test {
                filter {
                    excludeTestsMatching "ArchRulesTest"
                }
            }
        """

        when:
        succeeds('test')

        then:
        resultsFor().assertTestPathsExecuted(
            ':sample.OtherArchRulesTest:otherRule',
            ':sample.JupiterTest:someMethod()',
            ':sample.JupiterTest:otherMethod()'
        )
    }

    def 'runs only tests matching command line filter #filter when tests are declared as fields'() {
        when:
        succeeds('test', '--tests', filter)

        then:
        resultsFor().assertTestPathsExecuted(*expectedTestPaths)

        where:
        filter                                   | expectedTestPaths
        'ArchRulesTest.firstRule'                | [':sample.ArchRulesTest:firstRule']
        'sample.ArchRulesTest.firstRule'         | [':sample.ArchRulesTest:firstRule']
        'ArchRulesTest'                          | [':sample.ArchRulesTest:firstRule', ':sample.ArchRulesTest:secondRule', ':sample.ArchRulesTest:NestedRules:NestedRules > nestedRule']
        'ArchRulesTest.NestedRules > nestedRule' | [':sample.ArchRulesTest:NestedRules:NestedRules > nestedRule']
        '*Rule'                                  | [':sample.ArchRulesTest:firstRule', ':sample.ArchRulesTest:secondRule', ':sample.ArchRulesTest:NestedRules:NestedRules > nestedRule', ':sample.OtherArchRulesTest:otherRule']
        'JupiterTest.someMethod'                 | [':sample.JupiterTest:someMethod()']
        '*someMethod'                            | [':sample.JupiterTest:someMethod()']
    }
}
