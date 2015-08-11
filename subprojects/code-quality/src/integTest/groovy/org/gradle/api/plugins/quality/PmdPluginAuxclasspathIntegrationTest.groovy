/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.plugins.quality

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.hamcrest.Matcher

import static org.gradle.util.Matchers.containsLine
import static org.hamcrest.Matchers.containsString
import static org.hamcrest.Matchers.not

class PmdPluginAuxclasspathIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        writeBuildFile()
        file('settings.gradle') << 'include "pmd-rule", "rule-using"'
        auxclasspathRuleSetProject()
    }

    def "classpath configured for main sourceset"() {
        when:
        ruleUsingProject()

        then:
        succeeds ":rule-using:pmdMain"
        // since the classpath for the main sourceset is automatically set, the rule will find the junit class and report this
        file("rule-using/build/reports/pmd/main.xml").assertContents(containsClass("org.gradle.ruleusing.Class1"))
    }

    def "classpath overriden"() {
        when:
        ruleUsingProjectNoClasspath()

        then:
        succeeds ":rule-using:pmdMain"
        // since the classpath is cleared, the rule will not find the junit class, and not report this
        file("rule-using/build/reports/pmd/main.xml").assertContents(not(containsClass("org.gradle.ruleusing.Class1")))
    }

    private static Matcher<String> containsClass(String className) {
        containsLine(containsString(className.replace(".", File.separator)))
    }

    private void writeBuildFile() {
        file("build.gradle") << """
            allprojects {
                repositories {
                    mavenCentral()
                }
            }
        """
    }

    // Code for our rule using project
    private ruleUsingProject() {
        file("rule-using/build.gradle") << """
            apply plugin: "java"
            apply plugin: "pmd"

            dependencies {
                compile "junit:junit:3.8.1"

                pmd "net.sourceforge.pmd:pmd-core:5.3.3"
                pmd "net.sourceforge.pmd:pmd-java:5.3.3"
                pmd project(":pmd-rule")
            }

            pmd {
                ruleSets = ["java-auxclasspath"]
                ignoreFailures = true
            }
        """
        file("rule-using/src/main/java/org/gradle/ruleusing/Class1.java") << customCodeText()
    }

    private ruleUsingProjectNoClasspath() {
        file("rule-using/build.gradle") << """
            apply plugin: "java"
            apply plugin: "pmd"

            dependencies {
                compile "junit:junit:3.8.1"

                pmd "net.sourceforge.pmd:pmd-core:5.3.3"
                pmd "net.sourceforge.pmd:pmd-java:5.3.3"
                pmd project(":pmd-rule")
            }

            pmd {
                ruleSets = ["java-auxclasspath"]
                ignoreFailures = true
            }

            // Clear the classpath!
            project.tasks.getByName("pmdMain").classpath = files()
        """
        file("rule-using/src/main/java/org/gradle/ruleusing/Class1.java") << customCodeText()
    }

    private customCodeText() {
        """
            package org.gradle.ruleusing;

            import junit.framework.TestCase;

            public class Class1 extends TestCase {
            }
        """
    }

    // Code for our custom rule project
    private auxclasspathRuleSetProject() {
        file("pmd-rule/build.gradle") << """
            apply plugin: "java"

            dependencies {
                compile "net.sourceforge.pmd:pmd-core:5.3.3"
                compile "net.sourceforge.pmd:pmd-java:5.3.3"
            }
        """
        file("pmd-rule/src/main/resources/rulesets/java/auxclasspath.xml") << auxclasspathRuleSetText()
        file("pmd-rule/src/main/java/org/gradle/pmd/rules/AuxclasspathRule.java") << auxclasspathRuleCodeText()
    }

    private auxclasspathRuleCodeText() {
        """
            package org.gradle.pmd.rules;

            import net.sourceforge.pmd.lang.java.ast.ASTClassOrInterfaceDeclaration;
            import net.sourceforge.pmd.lang.java.ast.ASTClassOrInterfaceType;
            import net.sourceforge.pmd.lang.java.ast.ASTCompilationUnit;
            import net.sourceforge.pmd.lang.java.ast.ASTExtendsList;
            import net.sourceforge.pmd.lang.java.ast.ASTTypeDeclaration;
            import net.sourceforge.pmd.lang.java.rule.AbstractJavaRule;

            public class AuxclasspathRule extends AbstractJavaRule {

                private static final String JUNIT_TEST = "junit.framework.TestCase";

                @Override
                public Object visit(final ASTCompilationUnit node, final Object data) {
                    final ASTExtendsList astExtendsList = node.getFirstChildOfType(ASTTypeDeclaration.class)
                         .getFirstChildOfType(ASTClassOrInterfaceDeclaration.class)
                         .getFirstChildOfType(ASTExtendsList.class);

                    // ignore classes that don't extend another classes
                    if (astExtendsList == null) {
                        return super.visit(node, data);
                    }

                    final ASTClassOrInterfaceType astClassOrInterfaceType =
                        astExtendsList.getFirstChildOfType(ASTClassOrInterfaceType.class);


                    if (astClassOrInterfaceType.getType() != null
                      && astClassOrInterfaceType.getType().getName().equals(JUNIT_TEST)
                      && node.getClassTypeResolver().classNameExists(JUNIT_TEST)) {
                        addViolationWithMessage(data, node, "An auxclasspath is configured");
                    }
                    return super.visit(node, data);
                }
            }
        """
    }

    private auxclasspathRuleSetText() {
        """
            <ruleset name="auxclasspath"
                xmlns="http://pmd.sf.net/ruleset/2.0.0"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                xsi:schemaLocation="http://pmd.sf.net/ruleset/2.0.0 http://pmd.sf.net/ruleset_2_0_0.xsd"
                xsi:noNamespaceSchemaLocation="http://pmd.sf.net/ruleset_2_0_0.xsd">

                <description>Custom rule set</description>

                <rule name="Auxclasspath"
                    since="5.3.3"
                    message="An auxiliar classpath has been configured"
                    class="org.gradle.pmd.rules.AuxclasspathRule"
                    externalInfoUrl="http://pmd.sf.net/rules/java/typeresolution.html#Auxclasspath"
                    typeResolution="true">
                    <description>
                        Check if an auxiliar classpath is configured.
                    </description>
                    <priority>3</priority>
                    <example>
                        <![CDATA[
                        import com.monits.listener.JDBCCleanupContextListener;
                        public class Foo extends JDBCCleanupContextListener {
                        }
                        ]]>
                    </example>
                </rule>
            </ruleset>
        """
    }
}
