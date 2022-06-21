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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

// older Gradle version 6.7.1 is used in test
@Requires(TestPrecondition.JDK15_OR_EARLIER)
class GradlePluginWithVariantsPublicationIntegrationTest extends AbstractIntegrationSpec {

    def "can publish and use Gradle plugin with multiple variants"() {
        given:
        publishGradlePluginWithVariants()

        and:
        settingsFile """
            pluginManagement {
                repositories {
                    maven { url = '${mavenRepo.uri}' }
                }
            }
        """
        buildFile """
            plugins {
                id('com.example.greeting') version '1.0'
            }
        """

        when:
        succeeds 'greet'

        then:
        outputContains("Hello from Gradle 7.0+")

        when:
        def gradle6Result = runWithGradle6('greet')

        then:
        gradle6Result.assertOutputContains("Hello from Gradle <7.0")
    }

    def "can publish and compile against Gradle plugin with multiple variants in buildSrc"() {
        given:
        publishGradlePluginWithVariants()

        and:
        file('buildSrc/settings.gradle') << ''
        file('buildSrc/build.gradle') << """
            plugins {
                id('groovy-gradle-plugin')
            }
            dependencies {
                implementation('com.example:producer:1.0')
            }
            repositories {
                maven { url = '${mavenRepo.uri}' }
            }
        """
        file('buildSrc/src/main/groovy/my-plugin.gradle') << '''
            plugins {
                id('com.example.greeting')
            }
        '''

        and:
        settingsFile ""
        buildFile """
            plugins {
                id('my-plugin')
            }
        """

        when:
        succeeds 'greet'

        then:
        outputContains("Hello from Gradle 7.0+")

        when:
        def gradle6Result = runWithGradle6('greet')

        then:
        gradle6Result.assertOutputContains("Hello from Gradle <7.0")
    }

    private void publishGradlePluginWithVariants() {

        // given:
        def producer = file('producer')
        def pluginModule = mavenRepo.module('com.example', 'producer', '1.0')
        def pluginMarker = mavenRepo.module('com.example.greeting', 'com.example.greeting.gradle.plugin', '1.0')

        // and:
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

        // when:
        projectDir(producer)
        succeeds 'publish'

        // then:
        pluginModule.assertPublished()
        pluginMarker.assertPublished()
        pluginModule.artifact().assertPublished()
        pluginModule.artifact(classifier: 'gradle7').assertPublished()

        // and:
        def variants = pluginModule.parsedModuleMetadata.variants
        assert variants.size() == 4
        assert variants[0].name == "apiElements"
        assert !variants[0].attributes.containsKey('org.gradle.plugin.api-version')
        assert variants[1].name == "runtimeElements"
        assert !variants[1].attributes.containsKey('org.gradle.plugin.api-version')
        assert variants[2].name == "gradle7ApiElements"
        assert variants[2].attributes['org.gradle.plugin.api-version'] == '7.0'
        assert variants[3].name == "gradle7RuntimeElements"
        assert variants[3].attributes['org.gradle.plugin.api-version'] == '7.0'

        // cleanup:
        projectDir(testDirectory)
    }

    private ExecutionResult runWithGradle6(String... tasks) {
        return buildContext.distribution("6.7.1")
            .executer(temporaryFolder, buildContext)
            .withTasks('greet')
            .run()
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
