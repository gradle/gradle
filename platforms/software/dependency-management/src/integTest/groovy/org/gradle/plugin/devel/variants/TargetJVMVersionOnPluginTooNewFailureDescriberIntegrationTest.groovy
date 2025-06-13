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
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.jvm.JavaToolchainFixture
import org.gradle.internal.component.resolution.failure.exception.VariantSelectionByAttributesException
import org.gradle.internal.jvm.Jvm
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions

class TargetJVMVersionOnPluginTooNewFailureDescriberIntegrationTest extends AbstractIntegrationSpec implements JavaToolchainFixture {
    @Requires(value = IntegTestPreconditions.NotEmbeddedExecutor, reason = "must run with specific JDK that may differ from the current test JDK")
    def 'JVM version too low uses custom error message for plugin'() {
        given:
        def currentJdk = Jvm.current()
        def otherJdk = AvailableJavaHomes.differentVersion

        def lowerVersion = currentJdk.javaVersion.compareTo(otherJdk.javaVersion) < 0 ? currentJdk : otherJdk
        def higherVersion = currentJdk.javaVersion.compareTo(otherJdk.javaVersion) < 0 ? otherJdk : currentJdk

        println("Using JDK ${lowerVersion.javaVersion.majorVersion} as the consumer JDK and JDK ${higherVersion.javaVersion.majorVersion} as the producer JDK")

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

             java {
                targetCompatibility = ${higherVersion.javaVersion.majorVersion}
            }
            ${javaPluginToolchainVersion(higherVersion)}

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
        withInstallations(currentJdk, otherJdk)
        executer.withJvm(higherVersion)
        succeeds 'publish'

        then:
        pluginModule.assertPublished()
        pluginMarker.assertPublished()
        pluginModule.artifact([:]).assertPublished()

        when:
        projectDir(consumer)
        withInstallations(currentJdk, otherJdk)
        executer.withJvm(lowerVersion)
        fails 'greet', "--stacktrace"

        then:
        failure.assertHasErrorOutput("""> Could not resolve all artifacts for configuration 'classpath'.
   > Could not resolve com.example:producer:1.0.
     Required by:
         buildscript of root project 'consumer' > com.example.greeting:com.example.greeting.gradle.plugin:1.0
      > Dependency requires at least JVM runtime version ${higherVersion.javaVersion.majorVersion}. This build uses a Java ${lowerVersion.javaVersion.majorVersion} JVM.""")
        failure.assertHasErrorOutput("Caused by: " + VariantSelectionByAttributesException.class.getName())
        failure.assertHasResolution("Run this build using a Java ${higherVersion.javaVersion.majorVersion} or newer JVM.")
    }

    def 'JVM version too low uses custom error message for plugin when version attribute explicitly mis-set'() {
        given:
        Integer currentJava = Integer.valueOf(JavaVersion.current().majorVersion)
        Integer tooHighJava = currentJava + 1

        def producer = file('producer')
        def consumer = file('consumer')
        def pluginModule = mavenRepo.module('com.example', 'producer', '1.0')
        def pluginMarker = mavenRepo.module('com.example.greeting', 'com.example.greeting.gradle.plugin', '1.0')

        println("Using JDK $currentJava as the consumer JDK and JDK $tooHighJava as the producer JDK")

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
        failure.assertHasErrorOutput("""> Could not resolve all artifacts for configuration 'classpath'.
   > Could not resolve com.example:producer:1.0.
     Required by:
         buildscript of root project 'consumer' > com.example.greeting:com.example.greeting.gradle.plugin:1.0
      > Dependency requires at least JVM runtime version $tooHighJava. This build uses a Java $currentJava JVM.""")
        failure.assertHasErrorOutput("Caused by: " + VariantSelectionByAttributesException.class.getName())
        failure.assertHasResolution("Run this build using a Java $tooHighJava or newer JVM.")
    }

    @Requires(value = IntegTestPreconditions.NotEmbeddedExecutor, reason = "must run with specific JDK that may differ from the current test JDK")
    def 'JVM version too low uses custom error message for plugin when using composite build'() {
        given:
        def currentJdk = Jvm.current()
        def otherJdk = AvailableJavaHomes.differentVersion

        def lowerVersion = currentJdk.javaVersion.compareTo(otherJdk.javaVersion) < 0 ? currentJdk : otherJdk
        def higherVersion = currentJdk.javaVersion.compareTo(otherJdk.javaVersion) < 0 ? otherJdk : currentJdk

        println("Using JDK ${lowerVersion.javaVersion.majorVersion} as the consumer JDK and JDK ${higherVersion.javaVersion.majorVersion} as the producer JDK")

        and:
        def producer = file('producer')
        producer.file('build.gradle') << """
            plugins {
                id('java-gradle-plugin')
                id('maven-publish')
            }

            group = "com.example"
            version = "1.0"

            java {
                targetCompatibility = ${higherVersion.javaVersion.majorVersion}
            }
            ${javaPluginToolchainVersion(higherVersion)}

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

        and:
        def consumer = file('consumer')
        consumer.file('settings.gradle') << """
            pluginManagement {
                includeBuild '../producer'
                repositories {
                    maven { url = '${mavenRepo.uri}' }
                }
            }
        """
        consumer.file('build.gradle') << """
            plugins {
                id('com.example.greeting') version '1.0'
                id('java-library')
            }

            ${javaPluginToolchainVersion(lowerVersion)}
        """

        when:
        projectDir(consumer)
        withInstallations(currentJdk, otherJdk)
        executer.withJvm(lowerVersion)
        fails 'greet', "--stacktrace"

        then:
        failure.assertHasErrorOutput("""> Could not resolve all dependencies for configuration 'classpath'.
   > Could not resolve project :producer.
     Required by:
         buildscript of root project 'consumer'
      > Dependency requires at least JVM runtime version ${higherVersion.javaVersion.majorVersion}. This build uses a Java ${lowerVersion.javaVersion.majorVersion} JVM.""")
        failure.assertHasErrorOutput("Caused by: " + VariantSelectionByAttributesException.class.getName())
        failure.assertHasResolution("Run this build using a Java ${higherVersion.javaVersion.majorVersion} or newer JVM.")
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
                        System.out.println("Hello from Java ${JavaVersion.current().majorVersion} JVM!");
                    }
                }
            }
        """
    }
}
