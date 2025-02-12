/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.problems.deprecation

import org.gradle.api.internal.GradleInternal
import org.gradle.api.problems.internal.PluginIdLocation
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class DeprecationReporterIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        enableProblemsApiCheck()

        settingsFile('''
            includeBuild("plugin")
        ''')
        buildFile('''
            plugins {
                id 'com.example.my-plugin'
            }
        ''')
        buildFile("plugin/build.gradle", '''
            plugins {
                id 'java-gradle-plugin'
            }

            dependencies {
                implementation gradleApi()
            }

            gradlePlugin {
                plugins {
                   myPlugin {
                        id = 'com.example.my-plugin'
                        implementationClass = 'MyPlugin'
                    }
                }
            }
        ''')
    }

    def "plugin reports itself as deprecated"() {
        given:
        javaFile("plugin/src/main/java/MyPlugin.java", """
            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.problems.Problems;
            import ${ReportSource.name};

            import javax.inject.Inject;

            public abstract class MyPlugin implements Plugin<Project> {
                @Inject
                public abstract Problems getProblems();

                @Override
                public void apply(Project project) {
                    getProblems()
                        .getDeprecationReporter()
                        .deprecatePlugin(ReportSource.plugin("com.example.my-plugin"), "com.example.my-plugin", spec -> spec
                            .because("There's a better alternative")
                            .replacedBy("com.example.alternative-plugin")
                    );
                }
            }
        """)

        when:
        succeeds("help")

        then:
        verifyAll(receivedProblem) {
            definition.id.fqid == 'deprecation:plugin:com.example.my-plugin'
            contextualLabel == "Plugin 'com.example.my-plugin' is deprecated"
            details == "There's a better alternative"
            (originLocations[0] as PluginIdLocation).pluginId == 'com.example.my-plugin' // TODO (donat) we should have some location pointing to the plugin code. DefaultProblemLocationAnalyzer should identify locations from plugins, not just from build scripts
            // (contextualLocations[0] as PluginIdLocation).pluginId == 'com.example.my-plugin' // TODO (donat) we should have contextual location of who reported it
            exception != null
            additionalData.asMap['source']['id'] == 'com.example.my-plugin'
            additionalData.asMap['replacedBy'] == 'com.example.alternative-plugin'
        }
    }

    def "plugin reports another downstream dependency plugin deprecated"() {


        // how can plugins detect plugin dependencies?
        // maybe this is very similar to deprecating binary dependencies in a plugin. Like this using an old version of lib X will be an error in the next major release of this plugin
    }

    def "build script uses deprecated plugin API"() {

    }

    def "build script uses deprecated Gradle API"() {
        // TODO (doinat) should we use deprecatePlugin here?
    }

    def "plugin uses deprecated Gradle API"() { // TODO (donat) same test but during execution time
        given:
        javaFile("plugin/src/main/java/MyPlugin.java", """
            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.problems.Problems;
            import ${ReportSource.name};
            import ${GradleInternal.name};

            import javax.inject.Inject;

            public abstract class MyPlugin implements Plugin<Project> {
                @Inject
                public abstract Problems getProblems();

                @Override
                public void apply(Project project) {
                    ((GradleInternal) project.getGradle()).testDeprecation();
                }
            }
        """)
        when:
        executer.expectDeprecationWarning('The GradleInternal.testDeprecation() method has been deprecated. This is scheduled to be removed in Gradle 10.0. Please use the other() method instead.')
        succeeds('help')

        then:
        verifyAll(receivedProblem) {
            definition.id.fqid == 'deprecation:gradle:method:GradleInternal.testDeprecation()'
            contextualLabel == "Method 'org.gradle.api.internal.GradleInternal#testDeprecation()' is deprecated"
            details == null // to be replaced
            (originLocations[0] as PluginIdLocation).pluginId == 'com.example.my-plugin'
            // TODO (donat) we should have some location pointing to the plugin code. DefaultProblemLocationAnalyzer should identify locations from plugins, not just from build scripts
            // (contextualLocations[0] as PluginIdLocation).pluginId == 'com.example.my-plugin' // TODO (donat) we should have contextual location of who reported it
            exception != null
            // TODO (donat) do we need store the type?
            additionalData.asMap['source']['id'] == 'gradle'
            additionalData.asMap['removedIn']['version'] == '10.0'
            //additionalData.asMap['replacedBy'] == 'org.gradle.api.internal.GradleInternal#other()' // TODO (donat) implement
        }
    }

    def "plugin uses deprecated plugin API"() {

    }

//    def "can report generic deprecation"() {
//        given:
//        javaFile("plugin/src/main/java/DeprecationPlugin.java", """
//            import org.gradle.api.Plugin;
//            import org.gradle.api.Project;
//            import org.gradle.api.problems.Problems;
//
//            import javax.inject.Inject;
//
//            public abstract class DeprecationPlugin implements Plugin<Project> {
//                @Inject
//                public abstract Problems getProblems();
//
//                @Override
//                public void apply(Project project) {
//                    // Report the plugin as deprecated
//                    getProblems()
//                        .getDeprecationReporter()
//                        .deprecate("Generic deprecation message", feature -> feature
//                            .because("Reasoning of removal")
//                            .removedInVersion("2.0.0")
//                            .replacedBy("newMethod(String, String)")
//                        );
//                }
//            }
//            """)
//
//        when:
//        succeeds("help")
//
//        then:
//        def deprecation = receivedProblem
//        deprecation.definition.id.fqid == "deprecation:generic"
//        deprecation.definition.id.displayName == "Generic deprecation"
//        deprecation.contextualLabel == "Generic deprecation message"
//        verifyAll(deprecation.additionalData.asMap) {
//            it["because"] == "Reasoning of removal"
//            it["replacedBy"] == "newMethod(String, String)"
//            it["removedIn"]["version"] == "2.0.0"
//        }
//    }
//
//    def "can report method deprecation"() {
//        given:
//        javaFile("deprecation-plugin/src/main/java/DeprecationPlugin.java", """
//            import org.gradle.api.Plugin;
//import org.gradle.api.Project;
//import org.gradle.api.problems.Problems;
//
//import javax.inject.Inject;
//
//            public abstract class DeprecationPlugin implements Plugin<Project> {
//                @Inject
//                public abstract Problems getProblems();
//
//                @Override
//                public void apply(Project project) {
//                    // Report the plugin as deprecated
//                    getProblems().getDeprecationReporter().deprecateMethod(
//                        this.getClass(), "oldMethod(String, String)", feature -> feature
//                            .because("Reasoning of removal")
//                            .removedInVersion("2.0.0")
//                            .replacedBy("newMethod(String, String)")
//                    );
//                }
//            }
//            """)
//
//        when:
//        succeeds("help")
//
//        then:
//        def deprecation = receivedProblem
//        deprecation.definition.id.fqid == "deprecation:method"
//        deprecation.contextualLabel == "Method 'DeprecationPlugin_Decorated#oldMethod(String, String)' is deprecated"
//        verifyAll(deprecation.additionalData.asMap) {
//            it["because"] == "Reasoning of removal"
//            it["replacedBy"] == "newMethod(String, String)"
//            it["removedIn"]["version"] == "2.0.0"
//        }
//    }
}
