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

package org.gradle.plugin.devel.variants

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class UpgradeGradlePluginTransformsIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        buildTestFixture.withBuildInSubDir()
        singleProjectBuild("updateable-plugin") {
            file("src/main/java/com/example/UpdateablePlugin.java").java """
                package com.example;

                import org.gradle.api.*;

                public class UpdateablePlugin implements Plugin<Project> {
                    public void apply(Project project) {
                        System.out.println("Hello from updateable plugin");
                    }
                }
            """
            buildFile """
                apply plugin: 'java-gradle-plugin'

                gradlePlugin {
                    plugins {
                        greeting {
                            id = 'com.example.updateable-plugin'
                            implementationClass = 'com.example.UpdateablePlugin'
                        }
                    }
                }

                configurations {
                    runtimeElements {
                        attributes {
                            attribute(Attribute.of("org.gradle.plugin.runtime", String), "8.0")
                        }
                    }
//                    runtimeElementsForGradle8 {
//                        outgoing {
//                            artifact(tasks.jar)
//                        }
//                        attributes {
//                            def runtimeAttributes = configurations.runtimeElements.attributes
//                            runtimeAttributes.keySet().each { key ->
//                                attribute(key, runtimeAttributes.getAttribute(key))
//                            }
//
//                        }
//                    }
                }
            """
        }
        singleProjectBuild("old-plugin") {
            file("src/main/java/com/example/OldPlugin.java").java """
                package com.example;

                import org.gradle.api.*;

                public class OldPlugin implements Plugin<Project> {
                    public void apply(Project project) {
                        System.out.println("Hello from old plugin");
                    }
                }
            """
            buildFile """
                apply plugin: 'java-gradle-plugin'

                gradlePlugin {
                    plugins {
                        greeting {
                            id = 'com.example.old-plugin'
                            implementationClass = 'com.example.OldPlugin'
                        }
                    }
                }
            """
        }

        file("buildSrc/src/main/java/com/example/UpgradePluginTransform.java").java """
            package com.example;

            import org.gradle.api.*;
            import org.gradle.api.tasks.*;
            import org.gradle.api.file.*;
            import org.gradle.api.provider.*;
            import org.gradle.api.artifacts.transform.*;
            import javax.inject.Inject;
            import java.io.File;

            public abstract class UpgradePluginTransform implements TransformAction<TransformParameters.None> {
                @PathSensitive(PathSensitivity.NAME_ONLY)
                @InputArtifact
                public abstract Provider<FileSystemLocation> getInputArtifact();

                private File getInput() {
                    return getInputArtifact().get().getAsFile();
                }

                public void transform(TransformOutputs outputs) {
                    outputs.file(getInputArtifact());
                    System.out.println("Transformed " + getInput().getName());
                }
            }
        """
        settingsFile << """
            pluginManagement {
                includeBuild("old-plugin")
                includeBuild("updateable-plugin")
            }
            rootProject.name = "consumer"
        """
        buildFile """
            buildscript {
                def gradleRuntime = Attribute.of("org.gradle.plugin.runtime", String)

                configurations {
                    classpath {
                        println "Configuring classpath " + this
//                        attributes {
//                            attribute(gradleRuntime, GradleVersion.current().version)
//                        }
                    }
                }

                dependencies {
                    println "Configuring dependencies " + this

                    attributesSchema {
                        attribute(gradleRuntime) {

                        }
                    }
                    registerTransform(com.example.UpgradePluginTransform) {
                        from.attribute(gradleRuntime, "8.0")
                        to.attribute(gradleRuntime, GradleVersion.current().version)
                    }
                }
            }
            plugins {
                id 'com.example.old-plugin'
                id 'com.example.updateable-plugin'
            }
        """
    }

    def "can upgrade old plugins to latest with transforms"() {
//        when:
//        succeeds(":updateable-plugin:outgoingVariants", ":old-plugin:outgoingVariants")
//        then:
//        noExceptionThrown()

        when:
        succeeds("help")
        then:
        outputContains("Hello from old plugin")
        outputContains("Hello from updateable plugin")
    }
}
