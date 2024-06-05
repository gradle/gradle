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

package org.gradle.plugin.devel.variants

import org.gradle.api.JavaVersion
import org.gradle.api.plugins.UnknownPluginException
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.component.resolution.failure.exception.VariantSelectionException
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.util.GradleVersion
import spock.lang.Issue

class GradlePluginWithVariantsPublicationIntegrationTest extends AbstractIntegrationSpec {
    def currentGradle = GradleVersion.current().version

    @Requires(UnitTestPreconditions.Jdk15OrEarlier) // older Gradle version 6.7.1 is used in test
    def "can publish and use Gradle plugin with multiple variants"() {
        given:
        def producer = file('producer')
        def consumer = file('consumer')
        def pluginModule = mavenRepo.module('com.example', 'producer', '1.0')
        def pluginMarker = mavenRepo.module('com.example.greeting', 'com.example.greeting.gradle.plugin', '1.0')

        producer.file('settings.gradle') << ''
        producer.file('build.gradle') << """
            plugins {
                id('java-gradle-plugin')
                id('maven-publish')
            }

            group = "com.example"
            version = "1.0"

            // == Add a Gradle 7 variant
            def gradle7 = sourceSets.create('gradle7')
            java {
                registerFeature(gradle7.name) {
                    usingSourceSet(gradle7)
                    capability(project.group.toString(), project.name, project.version.toString())
                }
            }
            configurations.configureEach {
                if (canBeConsumed && name.startsWith(gradle7.name))  {
                    attributes {
                        attribute(GradlePluginApiVersion.GRADLE_PLUGIN_API_VERSION_ATTRIBUTE, objects.named(GradlePluginApiVersion, '7.0'))
                    }
                }
            }
            tasks.named(gradle7.processResourcesTaskName) {
                def copyPluginDescriptors = rootSpec.addChild()
                copyPluginDescriptors.into('META-INF/gradle-plugins')
                copyPluginDescriptors.from(tasks.pluginDescriptors)
            }
            // ==

            dependencies {
                gradle7CompileOnly(gradleApi()) // We should be able to access different Gradle API versions
                gradle7CompileOnly(localGroovy())
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
        producer.file('src/main/java/example/plugin/GreetingPlugin.java') << pluginImplementation('<7.0')
        producer.file('src/gradle7/java/example/plugin/GreetingPlugin.java') << pluginImplementation('7.0+')

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
        pluginModule.artifact().assertPublished()
        pluginModule.artifact(classifier: 'gradle7').assertPublished()

        def variants = pluginModule.parsedModuleMetadata.variants
        variants.size() == 4
        variants[0].name == "apiElements"
        !variants[0].attributes.containsKey('org.gradle.plugin.api-version')
        variants[1].name == "runtimeElements"
        !variants[1].attributes.containsKey('org.gradle.plugin.api-version')
        variants[2].name == "gradle7ApiElements"
        variants[2].attributes['org.gradle.plugin.api-version'] == '7.0'
        variants[3].name == "gradle7RuntimeElements"
        variants[3].attributes['org.gradle.plugin.api-version'] == '7.0'

        and:
        projectDir(consumer)
        succeeds 'greet'

        then:
        outputContains("Hello from Gradle 7.0+")

        and:
        def gradle6Executer = buildContext.distribution("6.7.1").executer(temporaryFolder, buildContext)
        def gradle6Result = gradle6Executer.usingProjectDirectory(consumer).withTasks('greet').run()

        then:
        gradle6Result.assertOutputContains("Hello from Gradle <7.0")
    }

    @Issue("https://github.com/gradle/gradle/issues/24609")
    def "when plugin request fails because plugin requires a higher version of Gradle, gets clear custom error message"() {
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
                        attribute(GradlePluginApiVersion.GRADLE_PLUGIN_API_VERSION_ATTRIBUTE, objects.named(GradlePluginApiVersion, '1000.0'))
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
        producer.file('src/main/java/example/plugin/GreetingPlugin.java') << pluginImplementation('>=1000.0')

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
      > Plugin com.example:producer:1.0 requires at least Gradle 1000.0. This build uses Gradle $currentGradle.""")
        failure.assertHasErrorOutput("Caused by: " + VariantSelectionException.class.getName())
        failure.assertHasResolution("Upgrade to at least Gradle 1000.0. See the instructions at https://docs.gradle.org/$currentGradle/userguide/upgrading_version_8.html#sub:updating-gradle.")
        failure.assertHasResolution("Downgrade plugin com.example:producer:1.0 to an older version compatible with Gradle $currentGradle.")
    }

    @Issue("https://github.com/gradle/gradle/issues/24609")
    def "when plugin request fails due to missing plugin, gets default failure message"() {
        given:
        def consumer = file('consumer')

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
        projectDir(consumer)
        fails 'greet', "--stacktrace"

        then:
        failure.assertHasDescription("""Plugin [id: 'com.example.greeting', version: '1.0'] was not found in any of the following sources:""")
        failure.assertHasErrorOutput(UnknownPluginException.class.getName())
    }

    @Issue("https://github.com/gradle/gradle/issues/24609")
    def "when plugin request fails due to missing plugin version, gets default error message"() {
        given:
        def producer = file('producer')
        def consumer = file('consumer')
        def pluginModule = mavenRepo.module('com.example', 'producer', '2.0')
        def pluginMarker = mavenRepo.module('com.example.greeting', 'com.example.greeting.gradle.plugin', '2.0')

        producer.file('settings.gradle').createFile()
        producer.file('build.gradle') << """
            plugins {
                id('java-gradle-plugin')
                id('maven-publish')
            }

            group = "com.example"
            version = "2.0"

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
        producer.file('src/main/java/example/plugin/GreetingPlugin.java') << pluginImplementation(GradleVersion.current().version)

        consumer.file('settings.gradle') << """
            pluginManagement {
                repositories {
                    maven { url = '${mavenRepo.uri}' }
                }
            }
        """
        consumer.file('build.gradle') << """
            plugins {
                id('com.example.greeting') version '1.0' // This version of the plugin isn't available
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
        failure.assertHasDescription("""Plugin [id: 'com.example.greeting', version: '1.0'] was not found in any of the following sources:""")
        failure.assertHasErrorOutput(UnknownPluginException.class.getName())
    }

    @Issue("https://github.com/gradle/gradle/issues/24609")
    def "when plugin request fails due to ambiguous plugin variants available, gets default error message"() {
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

            // Add an alternate variant
            def alternate = sourceSets.create('alternate')
            java {
                registerFeature(alternate.name) {
                    usingSourceSet(alternate)
                    capability(project.group.toString(), project.name, project.version.toString())
                }
            }

            def color = Attribute.of("color", String)
            configurations.configureEach {
                if (canBeConsumed && name.startsWith(alternate.name))  {
                    attributes {
                        attribute(color, 'green')
                    }
                } else if (canBeConsumed && !name.startsWith(alternate.name))  {
                    attributes {
                        attribute(color, 'blue')
                    }
                }
            }

            tasks.named(alternate.processResourcesTaskName) {
                def copyPluginDescriptors = rootSpec.addChild()
                copyPluginDescriptors.into('META-INF/gradle-plugins')
                copyPluginDescriptors.from(tasks.pluginDescriptors)
            }

            dependencies {
                alternateCompileOnly(gradleApi()) // We should be able to access different Gradle API versions
                alternateCompileOnly(localGroovy())
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
        producer.file('src/main/java/example/plugin/GreetingPlugin.java') << pluginImplementation('original')
        producer.file('src/alternate/java/example/plugin/GreetingPlugin.java') << pluginImplementation('alternate')

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
        pluginModule.artifact().assertPublished()
        pluginModule.artifact(classifier: 'alternate').assertPublished()

        def variants = pluginModule.parsedModuleMetadata.variants
        variants.size() == 4
        variants[0].name == "apiElements"
        variants[0].attributes['color'] == 'blue'
        variants[1].name == "runtimeElements"
        variants[1].attributes['color'] == 'blue'
        variants[2].name == "alternateApiElements"
        variants[2].attributes['color'] == 'green'
        variants[3].name == "alternateRuntimeElements"
        variants[3].attributes['color'] == 'green'

        when:
        projectDir(consumer)
        fails 'greet', "--stacktrace"

        then:
        failure.assertHasErrorOutput("""      > The consumer was configured to find a library for use during runtime, compatible with Java ${JavaVersion.current().majorVersion}, packaged as a jar, and its dependencies declared externally, as well as attribute 'org.gradle.plugin.api-version' with value '${GradleVersion.current().version}'. There are several available matching variants of com.example:producer:1.0
        The only attribute distinguishing these variants is 'color'. Add this attribute to the consumer's configuration to resolve the ambiguity:
          - Value: 'green' selects variant: 'alternateRuntimeElements'
          - Value: 'blue' selects variant: 'runtimeElements'""")
        failure.assertHasErrorOutput("Caused by: " + VariantSelectionException.class.name)
    }


    private static String pluginImplementation(String gradleVersion) {
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
                        System.out.println("Hello from Gradle $gradleVersion");
                    }
                }
            }
        """
    }
}
