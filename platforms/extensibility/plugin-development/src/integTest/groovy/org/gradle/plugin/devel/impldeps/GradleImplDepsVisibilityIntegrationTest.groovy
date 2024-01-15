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

import com.google.common.collect.Maps
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.testfixtures.ProjectBuilder

@Requires(IntegTestPreconditions.NotEmbeddedExecutor) // This tests class loader isolation which is not given in embedded mode
class GradleImplDepsVisibilityIntegrationTest extends BaseGradleImplDepsIntegrationTest {

    def "cannot compile against classes that are not part of Gradle's public API"() {
        when:
        buildFile << testablePluginProject()

        file('src/test/groovy/MyTest.groovy') << """
            class MyTest extends groovy.test.GroovyTestCase {

                void testImplIsHidden() {
                    try {
                        getClass().classLoader.loadClass("$Maps.name")
                        assert false : "expected $Maps.name not to be visible"
                    } catch (ClassNotFoundException ignore) {
                        // expected
                    }
                }
            }
        """

        then:
        succeeds 'build'
    }

    def "can reliably compile and unit test a plugin that depends on a conflicting version off a non-public Gradle API"() {
        when:
        buildFile << testablePluginProject()
        buildFile << """
            dependencies {
                implementation 'com.google.guava:guava:19.0'
            }
        """

        file('src/main/groovy/MyPlugin.groovy') << """
            import org.gradle.api.Plugin
            import org.gradle.api.Project
            import com.google.common.collect.Maps

            class MyPlugin implements Plugin<Project> {
                @Override
                void apply(Project project) {
                    println Maps.name
                }
            }
        """

        then:
        succeeds 'build'
    }

    def "can compile typical Java-based Gradle plugin using Gradle API"() {
        when:
        buildFile << applyJavaPlugin()
        buildFile << gradleApiDependency()

        file('src/main/java/MyPlugin.java') << """
            import org.gradle.api.Plugin;
            import org.gradle.api.Project;

            public class MyPlugin implements Plugin<Project> {
                @Override
                public void apply(Project project) {
                    System.out.println("Plugin applied!");
                }
            }
        """

        then:
        succeeds 'build'
    }

    def "can compile typical Groovy-based Gradle plugin using Gradle API without having to declare Groovy dependency"() {
        when:
        buildFile << applyGroovyPlugin()
        buildFile << gradleApiDependency()

        file('src/main/groovy/MyPlugin.groovy') << customGroovyPlugin()

        then:
        succeeds 'build'
    }

    def "can use ProjectBuilder to unit test a plugin"() {
        when:
        buildFile << testablePluginProject()

        file('src/main/groovy/MyPlugin.groovy') << customGroovyPlugin()

        file('src/test/groovy/MyTest.groovy') << """
            class MyTest extends groovy.test.GroovyTestCase {

                void testCanUseProjectBuilder() {
                    def project = ${ProjectBuilder.name}.builder().build()
                    project.plugins.apply(MyPlugin)
                    project.plugins.apply(org.gradle.api.plugins.JavaPlugin)
                    project.evaluate()
                }
            }
        """

        then:
        succeeds 'build'
    }
}
