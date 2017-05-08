/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.java

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class JavaLibraryOutgoingVariantsIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        def repo = mavenRepo
        repo.module("test", "api", "1.0").publish()
        repo.module("test", "compile", "1.0").publish()
        repo.module("test", "compile-only", "1.0").publish()
        repo.module("test", "runtime", "1.0").publish()
        repo.module("test", "implementation", "1.0").publish()
        repo.module("test", "runtime-only", "1.0").publish()

        settingsFile << "include 'a', 'b'"
        buildFile << """
def artifactType = Attribute.of('artifactType', String)

allprojects {
    repositories { maven { url '${mavenRepo.uri}' } }
}
project(':a') {
    apply plugin: 'java-library'
    dependencies {
        api 'test:api:1.0'
        compile 'test:compile:1.0'
        compileOnly 'test:compile-only:1.0'
        runtime 'test:runtime:1.0'
        implementation 'test:implementation:1.0'
        runtimeOnly 'test:runtime-only:1.0'
    }
}

project(':b') {
    configurations { consume }
    dependencies { consume project(':a') }
    task resolve {
        inputs.files configurations.consume
        doLast {
            println "files: " + configurations.consume.files.collect { it.name }
            configurations.consume.incoming.artifacts.each {
                println it.file.name + ' ' + it.variant.attributes
            }
        }
    }
}
"""
    }

    def "provides runtime JAR as default variant"() {
        when:
        run "resolve"

        then:
        result.assertTasksExecuted(":a:compileJava", ":a:processResources", ":a:classes", ":a:jar", ":b:resolve")
        result.assertOutputContains("files: [a.jar, api-1.0.jar, compile-1.0.jar, implementation-1.0.jar, runtime-1.0.jar, runtime-only-1.0.jar]")
        result.assertOutputContains("a.jar {artifactType=jar}")
    }

    def "provides API variant"() {
        buildFile << """
            project(':b') {
                configurations.consume.attributes.attribute(Usage.USAGE_ATTRIBUTE, Usage.FOR_COMPILE)
            }
"""
        when:
        run "resolve"

        then:
        result.assertTasksExecuted(":a:compileJava", ":b:resolve")
        result.assertOutputContains("files: [main, api-1.0.jar, compile-1.0.jar, runtime-1.0.jar]")
        result.assertOutputContains("main {artifactType=org.gradle.java.classes.directory, org.gradle.api.attributes.Usage=for compile}")
    }

    def "provides runtime variant"() {
        buildFile << """
            project(':b') {
                configurations.consume.attributes.attribute(Usage.USAGE_ATTRIBUTE, Usage.FOR_RUNTIME)
            }
"""
        when:
        run "resolve"

        then:
        result.assertTasksExecuted(":a:compileJava", ":a:processResources", ":a:classes", ":a:jar", ":b:resolve")
        result.assertOutputContains("files: [a.jar, api-1.0.jar, compile-1.0.jar, implementation-1.0.jar, runtime-1.0.jar, runtime-only-1.0.jar]")
        result.assertOutputContains("a.jar {artifactType=jar, org.gradle.api.attributes.Usage=for runtime}")
    }

    def "provides runtime JAR variant"() {
        buildFile << """
            project(':b') {
                configurations.consume.attributes.attribute(Usage.USAGE_ATTRIBUTE, Usage.FOR_RUNTIME)
                configurations.consume.attributes.attribute(artifactType, JavaPlugin.JAR_TYPE)
            }
"""
        when:
        run "resolve"

        then:
        result.assertTasksExecuted(":a:compileJava", ":a:processResources", ":a:classes", ":a:jar", ":b:resolve")
        result.assertOutputContains("files: [a.jar, api-1.0.jar, compile-1.0.jar, implementation-1.0.jar, runtime-1.0.jar, runtime-only-1.0.jar]")
        result.assertOutputContains("a.jar {artifactType=jar, org.gradle.api.attributes.Usage=for runtime}")
    }

    def "provides runtime classes variant"() {
        buildFile << """
            project(':b') {
                configurations.consume.attributes.attribute(Usage.USAGE_ATTRIBUTE, Usage.FOR_RUNTIME)
                configurations.consume.attributes.attribute(artifactType, JavaPlugin.CLASS_DIRECTORY)
                
                // TODO - should not require this
                dependencies.attributesSchema.attribute(artifactType).compatibilityRules.add(JavaArtifactTypesRule)
                dependencies.attributesSchema.attribute(artifactType).disambiguationRules.add(JavaArtifactTypesDisambiguateRule)
            }
            class JavaArtifactTypesRule implements AttributeCompatibilityRule<String> {
                void execute(CompatibilityCheckDetails<String> details) {
                    if (details.consumerValue == JavaPlugin.CLASS_DIRECTORY && details.producerValue == 'jar') {
                        details.compatible()
                    }
                    if (details.consumerValue == JavaPlugin.RESOURCES_DIRECTORY && details.producerValue == 'jar') {
                        details.compatible()
                    }
                }
            }
            class JavaArtifactTypesDisambiguateRule implements AttributeDisambiguationRule<String> {
                void execute(MultipleCandidatesDetails<String> details) {
                    if (details.candidateValues == [JavaPlugin.CLASS_DIRECTORY, 'jar'] as Set) {
                        details.closestMatch(JavaPlugin.CLASS_DIRECTORY)
                    }
                }
            }
"""
        when:
        run "resolve"

        then:
        result.assertTasksExecuted(":a:compileJava", ":b:resolve")
        result.assertOutputContains("files: [main, api-1.0.jar, compile-1.0.jar, implementation-1.0.jar, runtime-1.0.jar, runtime-only-1.0.jar]")
        result.assertOutputContains("main {artifactType=org.gradle.java.classes.directory, org.gradle.api.attributes.Usage=for runtime}")
    }

    def "provides runtime resources variant"() {
        buildFile << """
            project(':b') {
                configurations.consume.attributes.attribute(Usage.USAGE_ATTRIBUTE, Usage.FOR_RUNTIME)
                configurations.consume.attributes.attribute(artifactType, JavaPlugin.RESOURCES_DIRECTORY)

                // TODO - should not require this
                dependencies.attributesSchema.attribute(artifactType).compatibilityRules.add(JavaArtifactTypesRule)
                dependencies.attributesSchema.attribute(artifactType).disambiguationRules.add(JavaArtifactTypesDisambiguateRule)
            }
            class JavaArtifactTypesRule implements AttributeCompatibilityRule<String> {
                void execute(CompatibilityCheckDetails<String> details) {
                    if (details.consumerValue == JavaPlugin.CLASS_DIRECTORY && details.producerValue == 'jar') {
                        details.compatible()
                    }
                    if (details.consumerValue == JavaPlugin.RESOURCES_DIRECTORY && details.producerValue == 'jar') {
                        details.compatible()
                    }
                }
            }
            class JavaArtifactTypesDisambiguateRule implements AttributeDisambiguationRule<String> {
                void execute(MultipleCandidatesDetails<String> details) {
                    if (details.candidateValues == [JavaPlugin.RESOURCES_DIRECTORY, 'jar'] as Set) {
                        details.closestMatch(JavaPlugin.RESOURCES_DIRECTORY)
                    }
                }
            }
"""

        when:
        run "resolve"

        then:
        result.assertTasksExecuted(":a:processResources", ":b:resolve")
        result.assertOutputContains("files: [main, api-1.0.jar, compile-1.0.jar, implementation-1.0.jar, runtime-1.0.jar, runtime-only-1.0.jar]")
        result.assertOutputContains("main {artifactType=org.gradle.java.resources.directory, org.gradle.api.attributes.Usage=for runtime}")
    }
}
