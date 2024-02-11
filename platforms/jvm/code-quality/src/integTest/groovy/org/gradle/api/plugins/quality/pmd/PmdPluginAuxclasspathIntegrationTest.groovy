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

import org.gradle.util.internal.VersionNumber
import org.hamcrest.Matcher
import org.junit.Assume

import static org.gradle.util.Matchers.containsLine
import static org.gradle.util.Matchers.containsText
import static org.hamcrest.CoreMatchers.containsString

class PmdPluginAuxclasspathIntegrationTest extends AbstractPmdPluginVersionIntegrationTest {
    private static final String ASSERTJ_STR = "'org.assertj:assertj-core:3.23.1'" // Some arbitrary dependency which is not already on the worker classpath.
    private static final String ASSERTJ_IMPL_DEPENDENCY = "implementation $ASSERTJ_STR"
    private static final String ASSERTJ_COMPILE_ONLY_DEPENDENCY = "compileOnly $ASSERTJ_STR"
    private static final String ASSERTJ_PMDAUX_DEPENDENCY = "pmdAux $ASSERTJ_STR"

    static boolean supportsAuxclasspath() {
        return VersionNumber.parse("5.2.0") < versionNumber
    }

    def setup() {
        includeProject("pmd-rule")
        buildFile << """
            allprojects {
                ${mavenCentralRepository()}

                apply plugin: 'java'

                ${requiredSourceCompatibility()}
            }

            project("pmd-rule") {
                dependencies {
                    implementation "${calculateDefaultDependencyNotation()}"
                }
            }
        """.stripIndent()

        file("pmd-rule/src/main/resources/rulesets/java/auxclasspath.xml") << rulesetXml()
        file("pmd-rule/src/main/java/org/gradle/pmd/rules/AuxclasspathRule.java") << ruleCode()
    }

    private void setupRuleUsingProject(String analyzedCode, String... dependencies) {
        includeProject("rule-using")

        String dependenciesString = dependencies.join('\n')
        buildFile << """
            project("rule-using") {
                apply plugin: 'pmd'

                dependencies {
                    $dependenciesString

                    pmd project(":pmd-rule")
                }

                pmd {
                    ruleSets = ["rulesets/java/auxclasspath.xml"]
                    ${supportIncrementalAnalysis() ? "" : "incrementalAnalysis = false"}
                }
            }
        """.stripIndent()

        file("rule-using/src/main/java/org/gradle/ruleusing/Class1.java") << analyzedCode
    }

    private void setupIntermediateProject(String dependency) {
        includeProject("intermediate")
        buildFile << """
            project("intermediate") {
                dependencies {
                    $dependency
                }
            }
        """.stripIndent()

        file("intermediate/src/main/java/org/gradle/intermediate/IntermediateClass.java") << """
        package org.gradle.intermediate;

        public class IntermediateClass {
            private static org.assertj.core.api.Condition<Object> condition = null;
        }
        """.stripIndent()
    }

    def "auxclasspath configured for rule-using project"() {
        Assume.assumeTrue(supportsAuxclasspath() && fileLockingIssuesSolved())

        given:
        setupRuleUsingProject(classExtendingAssertj(), ASSERTJ_IMPL_DEPENDENCY)

        expect:
        fails ":rule-using:pmdMain"

        file("rule-using/build/reports/pmd/main.xml").
            assertContents(containsClass("org.gradle.ruleusing.Class1")).
            assertContents(containsText("auxclasspath configured"))
    }

    def "auxclasspath configured for test sourceset of rule-using project"() {
        Assume.assumeTrue(supportsAuxclasspath() && fileLockingIssuesSolved())

        given:
        setupRuleUsingProject(classExtendingAssertj(), ASSERTJ_IMPL_DEPENDENCY)

        file("rule-using/src/test/java/org/gradle/ruleusing/Class2.java") << testClass()

        expect:
        fails ":rule-using:pmdTest"

        file("rule-using/build/reports/pmd/test.xml").
            assertContents(containsClass("org.gradle.ruleusing.Class2")).
            assertContents(containsText("auxclasspath configured"))
    }

    def "auxclasspath not configured properly for rule-using project"() {
        Assume.assumeTrue(supportsAuxclasspath() && fileLockingIssuesSolved())

        given:
        setupRuleUsingProject(classExtendingAssertj(), ASSERTJ_IMPL_DEPENDENCY)

        buildFile << """
project("rule-using") {
    tasks.withType(Pmd) {
        // clear the classpath
        classpath = files()
    }
}
"""
        expect:
        fails ":rule-using:pmdMain"

        file("rule-using/build/reports/pmd/main.xml").
            assertContents(containsClass("org.gradle.ruleusing.Class1")).
            assertContents(containsText("auxclasspath not configured"))
    }

    def "auxclasspath contains transitive implementation dependencies"() {
        Assume.assumeTrue(supportsAuxclasspath() && fileLockingIssuesSolved())

        setupIntermediateProject(ASSERTJ_IMPL_DEPENDENCY)
        setupRuleUsingProject(classReferencingIntermediate(), "implementation project(':intermediate')")

        expect:
        fails ":rule-using:pmdMain"

        file("rule-using/build/reports/pmd/main.xml").
            assertContents(containsClass("org.gradle.ruleusing.Class1")).
            assertContents(containsText("auxclasspath configured"))
    }

    def "auxclasspath does not contain transitive compileOnly dependencies"() {
        Assume.assumeTrue(supportsAuxclasspath() && fileLockingIssuesSolved())

        setupIntermediateProject(ASSERTJ_COMPILE_ONLY_DEPENDENCY)
        setupRuleUsingProject(classReferencingIntermediate(), "implementation project(':intermediate')")

        expect:
        fails ":rule-using:pmdMain"

        file("rule-using/build/reports/pmd/main.xml").
            assertContents(containsClass("org.gradle.ruleusing.Class1")).
            assertContents(containsText("auxclasspath not configured"))
    }

    def "auxclasspath contains pmdAux dependencies"() {
        Assume.assumeTrue(supportsAuxclasspath() && fileLockingIssuesSolved())

        setupIntermediateProject(ASSERTJ_COMPILE_ONLY_DEPENDENCY)
        setupRuleUsingProject(classReferencingIntermediate(), "implementation project(':intermediate')", ASSERTJ_PMDAUX_DEPENDENCY)

        expect:
        fails ":rule-using:pmdMain"

        file("rule-using/build/reports/pmd/main.xml").
            assertContents(containsClass("org.gradle.ruleusing.Class1")).
            assertContents(containsText("auxclasspath configured"))
    }

    private static Matcher<String> containsClass(String className) {
        containsLine(containsString(className.replace(".", File.separator)))
    }

    private static ruleCode() {
        """
            package org.gradle.pmd.rules;

            import net.sourceforge.pmd.lang.java.ast.ASTCompilationUnit;
            import net.sourceforge.pmd.lang.java.rule.AbstractJavaRule;

            public class AuxclasspathRule extends AbstractJavaRule {

                private static final String ASSERTJ_TEST = "org.assertj.core.configuration.Configuration";
                private static final String CLASS1 = "org.gradle.ruleusing.Class1";

                @Override
                public Object visit(final ASTCompilationUnit node, final Object data) {
                    if (node.getClassTypeResolver().classNameExists(ASSERTJ_TEST)
                        && node.getClassTypeResolver().classNameExists(CLASS1)) {
                        addViolationWithMessage(data, node, "auxclasspath configured.");
                    } else {
                        addViolationWithMessage(data, node, "auxclasspath not configured.");
                    }
                    return super.visit(node, data);
                }
            }
        """
    }

    private static rulesetXml() {
        """
            <ruleset name="auxclasspath"
                xmlns="http://pmd.sf.net/ruleset/2.0.0"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                xsi:schemaLocation="http://pmd.sf.net/ruleset/2.0.0 http://pmd.sf.net/ruleset_2_0_0.xsd"
                xsi:noNamespaceSchemaLocation="http://pmd.sf.net/ruleset_2_0_0.xsd">

                <rule name="Auxclasspath"
                    class="org.gradle.pmd.rules.AuxclasspathRule"
                    typeResolution="true">
                </rule>
            </ruleset>
        """
    }

    private static classExtendingAssertj() {
        """
            package org.gradle.ruleusing;
            public class Class1 extends org.assertj.core.configuration.Configuration { }
        """
    }

    private static testClass() {
        """
            package org.gradle.ruleusing;
            public class Class2 extends Class1 { }
        """
    }

    private static classReferencingIntermediate() {
        """
        package org.gradle.ruleusing;
        public class Class1 {
            private org.gradle.intermediate.IntermediateClass mClass = null;
        }
       """
    }

    private void includeProject(String projectName) {
        settingsFile << "include '$projectName'\n"
    }
}
