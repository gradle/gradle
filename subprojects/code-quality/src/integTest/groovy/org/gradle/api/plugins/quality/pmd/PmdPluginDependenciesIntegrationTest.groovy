/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.plugins.quality.pmd

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class PmdPluginDependenciesIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        buildFile << """
            plugins {
                id 'java-library'
                id 'pmd'
            }

            ${mavenCentralRepository()}

            testing.suites.test.useJUnit()
            tasks.withType(Pmd) {
                // clear the classpath to avoid file locking issues on PMD version < 5.5.1
                classpath = files()
            }
        """
        badCode()
    }

    def "allows configuring tool dependencies explicitly"() {
        def testDependency = 'net.sourceforge.pmd:pmd:5.1.1'
        expect: //defaults exist and can be inspected
        succeeds("dependencies", "--configuration", "pmd")
        output.contains "pmd:pmd-java:"

        when:
        buildFile << """
            dependencies {
                //downgrade version:
                pmd "$testDependency"
            }

            pmd {
                incrementalAnalysis = false
            }
        """.stripIndent()

        then:
        fails("check")
        failure.assertHasDescription("Execution failed for task ':pmdTest'.")
        and:
        succeeds("dependencies", "--configuration", "pmd")
        output.contains "$testDependency"
    }

    def "fails properly using older version of PMD without incremental analysis support"() {
        given:
        buildFile << """
            dependencies {
                //downgrade version:
                pmd "net.sourceforge.pmd:pmd:5.1.1"
            }
        """.stripIndent()

        when:
        fails("pmdMain")

        then:
        failure.assertHasCause("Incremental analysis only supports PMD 6.0.0 and newer. Please upgrade from PMD 5.1.1 or disable incremental analysis.")

        when:
        fails("pmdTest")

        then:
        failure.assertHasCause("Incremental analysis only supports PMD 6.0.0 and newer. Please upgrade from PMD 5.1.1 or disable incremental analysis.")

        when:
        fails("check")

        then:
        failure.assertHasFailures(2)
    }


    private badCode() {
        // No Warnings
        file("src/main/java/org/gradle/Class1.java") <<
            "package org.gradle; class Class1 { public boolean isFoo(Object arg) { return true; } }"
        // PMD Lvl 2 Warning BooleanInstantiation
        // PMD Lvl 3 Warning OverrideBothEqualsAndHashcode
        file("src/test/java/org/gradle/Class1Test.java") <<
            """
            package org.gradle;

            import static org.junit.Assert.assertTrue;

            import org.junit.Test;

            public class Class1Test<T> {
                @Test
                public void testFoo() {
                    Class1 c = new Class1();
                    assertTrue(c.isFoo("foo"));
                }

                public boolean equals(Object arg) { return java.lang.Boolean.valueOf(true); }
            }
            """
    }
}
