/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.plugin.devel.impldeps


import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Issue

@Requires(IntegTestPreconditions.NotEmbeddedExecutor) // This tests class loader isolation which is not given in embedded mode
class GradleImplDepsShadingIssuesIntegrationTest extends BaseGradleImplDepsIntegrationTest {

    @Issue("GRADLE-3456")
    def "doesn't fail when using Ivy in a plugin"() {

        when:
        buildFile << testablePluginProject()
        file('src/main/groovy/MyPlugin.groovy') << """
            import org.gradle.api.Plugin
            import org.gradle.api.Project

            class MyPlugin implements Plugin<Project> {

                void apply(Project project) {
                    def conf = project.configurations.create('bug')
                    project.${mavenCentralRepository()}

                    project.dependencies {
                        bug 'junit:junit:4.13'
                    }
                    conf.resolve()
                }
            }
        """
        file('src/test/groovy/MyPluginTest.groovy') << pluginTest()

        then:
        succeeds 'test'
    }

    private static String pluginTest() {
        """
            class MyPluginTest extends groovy.test.GroovyTestCase {

                void testCanUseProjectBuilder() {
                    def project = ${ProjectBuilder.name}.builder().build()
                    project.plugins.apply(MyPlugin)
                    project.evaluate()
                }
            }
        """
    }

    def "can read resources both with relative and absolute path in relocated and original path"() {

        when:
        buildFile << testablePluginProject()
        file('src/main/groovy/MyPlugin.groovy') << '''
            import org.gradle.api.Plugin
            import org.gradle.api.Project

            @groovy.transform.CompileStatic
            class MyPlugin implements Plugin<Project> {

                void apply(Project project) {
                    Class ivy = Class.forName('org.gradle.internal.impldep.org.apache.ivy.plugins.parser.m2.PomReader')
                    assert ivy.getResource('m2-entities.ent')
                    assert ivy.getResource('/org/apache/ivy/plugins/parser/m2/m2-entities.ent')
                    assert ivy.getResource('/org/gradle/internal/impldep/org/apache/ivy/plugins/parser/m2/m2-entities.ent')

                    byte[] original = ivy.getResourceAsStream('/org/apache/ivy/plugins/parser/m2/m2-entities.ent').bytes
                    byte[] relocated = ivy.getResourceAsStream('/org/gradle/internal/impldep/org/apache/ivy/plugins/parser/m2/m2-entities.ent').bytes
                    assert original.length > 0
                    assert Arrays.equals(original, relocated)
                }
            }
        '''
        file('src/test/groovy/MyPluginTest.groovy') << pluginTest()

        then:
        succeeds 'test'
    }

    @Issue("GRADLE-3525")
    def "can use newer Servlet API"() {
        when:
        buildFile << testablePluginProject()


        buildFile << """
            dependencies {
                testImplementation "javax.servlet:javax.servlet-api:3.1.0"
            }
        """

        file('src/test/groovy/ServletApiTest.groovy') << '''
            import org.junit.Test

            public class ServletApiTest {

                @Test
                public void canLoadNewerServletApi() {
                    Class clazz = Class.forName("javax.servlet.AsyncContext")
                    URL source = clazz.classLoader.getResource("javax/servlet/http/HttpServletRequest.class")
                    assert source.toString().contains('servlet-api-3.1.0')
                }
            }
        '''.stripIndent()

        then:
        succeeds 'test'
    }

    @Issue("https://github.com/gradle/gradle/issues/3780")
    def "can use different JGit API"() {
        when:
        buildFile << testablePluginProject()

        buildFile << """
            dependencies {
                testImplementation 'org.eclipse.jgit:org.eclipse.jgit:4.9.1.201712030800-r'
            }
        """

        file('src/test/groovy/JGitTest.groovy') << '''
            import org.junit.Test

            class JGitTest {
                @Test
                void loadJGitResources() {
                    assert org.eclipse.jgit.internal.JGitText.getPackage().getImplementationVersion() == "4.9.1.201712030800-r"
                    assert org.eclipse.jgit.internal.JGitText.get() != null
                    assert org.gradle.internal.impldep.org.eclipse.jgit.internal.JGitText.get() != null
                }
            }
        '''.stripIndent()

        then:
        succeeds 'test'
    }
}
