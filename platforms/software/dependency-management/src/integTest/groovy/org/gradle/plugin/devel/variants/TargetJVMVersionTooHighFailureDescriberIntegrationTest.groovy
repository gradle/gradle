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

package org.gradle.plugin.devel.variants

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.component.resolution.failure.exception.VariantSelectionException

class TargetJVMVersionTooHighFailureDescriberIntegrationTest extends AbstractIntegrationSpec {
    Integer currentJava = Integer.valueOf(JavaVersion.current().majorVersion)
    Integer tooHighJava = currentJava + 1

    def 'JVM version too low uses custom error message for dependency'() {
        given:
        def producer = file('producer')
        def consumer = file('consumer')

        file('settings.gradle') << """
            include 'producer', 'consumer'
        """

        producer.file('build.gradle') << """
            plugins {
                id('java-library')
            }

            configurations.configureEach {
                if (canBeConsumed)  {
                    attributes {
                        attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, $tooHighJava)
                    }
                }
            }
        """

        consumer.file('build.gradle') << """
            plugins {
                id('java-library')
            }

            dependencies {
                implementation project(':producer')
            }
        """

        when:
        fails ':consumer:build', "--stacktrace"

        then:
        failure.assertHasErrorOutput("""> Could not resolve all task dependencies for configuration ':consumer:compileClasspath'.
   > Could not resolve project :producer.
     Required by:
         project :consumer
      > project :producer requires at least a Java $tooHighJava JVM. This build uses a Java $currentJava JVM.""")
        failure.assertHasErrorOutput("Caused by: " + VariantSelectionException.class.getName())
        failure.assertHasResolution("Run this build using a Java $tooHighJava JVM (or newer).")
    }

    def 'JVM version too low uses custom error message for plugin'() {
        given:
        def producer = file('producer')
        def consumer = file('consumer')
        def pluginModule = mavenRepo.module('com.example', 'producer', '1.0')
        def pluginMarker = mavenRepo.module('com.example.greeting', 'com.example.greeting.gradle.plugin', '1.0')

        producer.file('settings.gradle').createFile()
        producer.file('build.gradle') << """
            plugins {
                id('java-gradle-plugin')
                id('maven-publish')
            }

            group = "com.example"
            version = "1.0"

            configurations.configureEach {
                if (canBeConsumed)  {
                    attributes {
                        attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, $tooHighJava)
                    }
                }
            }

            gradlePlugin {
                plugins.create('greeting') {
                    id = 'com.example.greeting'
                    implementationClass = 'example.plugin.GreetingPlugin'
                }
            }
            publishing {
                repositories {
                    maven { url = '${mavenRepo.uri}' }
                }
            }
        """
        producer.file('src/main/java/example/plugin/GreetingPlugin.java') << pluginImplementation()

        consumer.file('settings.gradle') << """
            pluginManagement {
                repositories {
                    maven { url = '${mavenRepo.uri}' }
                }
            }
        """
        consumer.file('build.gradle') << """
            plugins {
                id('com.example.greeting') version '1.0'
            }
        """

        when:
        projectDir(producer)
        succeeds 'publish'

        then:
        pluginModule.assertPublished()
        pluginMarker.assertPublished()
        pluginModule.artifact([:]).assertPublished()

        when:
        projectDir(consumer)
        fails 'greet', "--stacktrace"

        then:
        failure.assertHasErrorOutput("""> Could not resolve all artifacts for configuration ':classpath'.
   > Could not resolve com.example:producer:1.0.
     Required by:
         project : > com.example.greeting:com.example.greeting.gradle.plugin:1.0
      > com.example:producer:1.0 requires at least a Java $tooHighJava JVM. This build uses a Java $currentJava JVM.""")
        failure.assertHasErrorOutput("Caused by: " + VariantSelectionException.class.getName())
        failure.assertHasResolution("Run this build using a Java $tooHighJava JVM (or newer).")
    }

    private String pluginImplementation() {
        """
            package example.plugin;

            import org.gradle.api.Project;
            import org.gradle.api.Plugin;
            import org.gradle.api.DefaultTask;
            import org.gradle.api.tasks.TaskAction;

            public class GreetingPlugin implements Plugin<Project> {
                public void apply(Project project) {
                    project.getTasks().register("greet", GreetTask.class);
                }

                public static class GreetTask extends DefaultTask {
                    @TaskAction
                    public void greet() {
                        System.out.println("Hello from Java $currentJava JVM!");
                    }
                }
            }
        """
    }

}
