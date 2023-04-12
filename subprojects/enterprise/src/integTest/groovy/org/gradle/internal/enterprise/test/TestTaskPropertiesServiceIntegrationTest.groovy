/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.enterprise.test

import groovy.json.JsonGenerator
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.jvm.Jvm
import org.gradle.test.fixtures.plugin.PluginBuilder

import javax.inject.Inject
import java.util.stream.Stream

class TestTaskPropertiesServiceIntegrationTest extends AbstractIntegrationSpec {

    def "provides task configuration to plugin"() {
        given:
        new PluginBuilder(file("buildSrc")).with {
            addPlugin("""
                def objects = project.objects
                def outputFile = project.file("\${project.buildDir}/testTaskProperties.json")
                def service = project.services.get(${TestTaskPropertiesService.name})
                project.tasks.named('test').configure {
                    doFirst { task ->
                        def properties = service.collectProperties(task)

                        def generator = new ${JsonGenerator.Options.name}()
                            .addConverter(new groovy.json.JsonGenerator.Converter() {
                                @Override
                                boolean handles(Class<?> type) {
                                    ${Stream.name}.isAssignableFrom(type)
                                }
                                @Override
                                Object convert(Object value, String key) {
                                    (value as ${Stream.name}).toArray()
                                }
                            })
                            .addConverter(new ${JsonGenerator.Converter.name}() {
                                @Override
                                boolean handles(Class<?> type) {
                                    ${File.name}.isAssignableFrom(type)
                                }
                                @Override
                                Object convert(Object value, String key) {
                                    (value as ${File.name}).absolutePath
                                }
                            })
                            .build()

                        def json = ${JsonOutput.name}.prettyPrint(generator.toJson(properties))

                        outputFile.parentFile.mkdirs()
                        outputFile.text = json
                    }
                }
            """)
            generateForBuildSrc()
        }

        and:
        buildFile << """
            plugins {
                id 'java'
                id 'test-plugin'
            }
            ${mavenCentralRepository()}
            dependencies {
                testImplementation('org.junit.jupiter:junit-jupiter:5.9.2')
                testRuntimeOnly('org.junit.platform:junit-platform-launcher')
            }
            test {
                forkEvery = 42
                filter {
                    includeTestsMatching('*Class')
                    excludeTestsMatching('exclude-pattern')
                }
                useJUnitPlatform {
                    includeTags('included-tag')
                    excludeTags('excluded-tag')
                    includeEngines('included-engine')
                    excludeEngines('excluded-engine')
                }
                jvmArgs('-Dkey=value')
                environment('KEY', 'VALUE')
            }
        """
        file('src/test/java/org/example/SomeClass.java') << """
            package org.example;
            public class SomeClass {}
        """

        when:
        fails('test', '--tests', 'Test*')

        then:
        failureCauseContains("No tests found for given includes")

        and:
        def expectedClasspath = [
            file('build/classes/java/test'),
            file('build/resources/test'),
            file('build/classes/java/main'),
            file('build/resources/main'),
        ]
        def expectedExecutable = Jvm.current().javaExecutable.absolutePath
        def expectedJavaVersion = JavaVersion.current().majorVersion.toInteger()
        with(new JsonSlurper().parse(file('build/testTaskProperties.json')), Map) {
            usingJUnitPlatform == true
            forkEvery == 42
            with(filters, Map) {
                includePatterns == ['*Class']
                excludePatterns == ['exclude-pattern']
                commandLineIncludePatterns == ['Test*']
                includeTags == ['included-tag']
                excludeTags == ['excluded-tag']
                includeEngines == ['included-engine']
                excludeEngines == ['excluded-engine']
            }
            with(forkOptions, Map) {
                workingDir == this.testDirectory.absolutePath
                executable == expectedExecutable
                javaMajorVersion == expectedJavaVersion
                classpath.collect { new File(it as String) }.containsAll(expectedClasspath)
                modulePath == []
                jvmArgs.contains('-Dkey=value')
                environment.KEY == 'VALUE'
            }
            with(candidateClassFiles, List) {
                size() == 1
                with(first(), Map) {
                    file == file("build/classes/java/test/org/example/SomeClass.class").absolutePath
                    relativePath == "org/example/SomeClass.class"
                }
            }
            with(inputFileProperties, List) {
                !empty
                with(it.find { it instanceof Map && it['propertyName'] == 'stableClasspath' }, Map) {
                    files.collect { new File(it as String) }.containsAll(expectedClasspath)
                }
            }
            with(outputFileProperties, List) {
                !empty
                with(it.find { it instanceof Map && it['propertyName'] == 'reports.enabledReports.html.outputLocation' }, Map) {
                    type == 'DIRECTORY'
                    files.collect { new File(it as String) } == [file('build/reports/tests/test')]
                }
            }
        }
    }

    def "can be used to disable storing task in build cache"() {
        given:
        def cacheDir = createDir("cache-dir")
        settingsFile << """
            buildCache {
                local {
                    directory = file("${cacheDir.name}")
                }
            }
        """
        buildFile << """
            plugins {
                id 'java'
            }
            ${mavenCentralRepository()}
            testing.suites.test.useJUnit()
            test.doLast(objects.newInstance(Disabler))
            abstract class Disabler implements Action<Task> {
                private final ${TestTaskPropertiesService.name} service
                @${Inject.name} Disabler(${TestTaskPropertiesService.name} service) {
                    this.service = service
                }
                @Override void execute(Task task) {
                    service.doNotStoreInCache(task)
                }
            }
        """
        file('src/test/java/org/example/TestClass.java') << """
            package org.example;
            public class TestClass {}
        """

        when:
        succeeds('clean', 'test', '--build-cache')
        succeeds('clean', 'test', '--build-cache')

        then:
        executedAndNotSkipped(':test')
    }
}
