/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.cc.impl

import spock.lang.Issue

class ConfigurationCacheBuildScriptClasspathIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    @Issue("https://github.com/gradle/gradle/issues/36177")
    def "can use a project from included build in buildscript classpath when the project is an input for work"() {
        file("build-logic/settings.gradle") << ""
        file("build-logic/build.gradle") << """
            plugins {
                id("java-library")
            }
            group = "org.test"
            version = "1.0"
        """

        settingsFile """
            includeBuild("build-logic")
        """

        buildFile """
            import org.gradle.api.artifacts.transform.TransformParameters
            buildscript {
                dependencies {
                    classpath("org.test:build-logic:1.0")
                }
            }

            plugins {
                id("java-library")
            }

            abstract class FooTransform implements TransformAction<TransformParameters.None> {
                @InputArtifact
                abstract Provider<FileSystemLocation> getInput()
                void transform(TransformOutputs outputs) {
                    def inputFile = input.get().asFile
                    outputs.file(inputFile.name + ".foo").createNewFile()
                }
            }

            dependencies {
                registerTransform(FooTransform) {
                    from.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "jar")
                    to.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "foo")
                }
                implementation("org.test:build-logic:1.0")
            }

            tasks.register("syncFoo", Sync) {
                from(configurations.runtimeClasspath.incoming.artifactView {
                    attributes.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "foo")
                }.files)
                into(layout.buildDirectory)
            }
        """

        when:
        configurationCacheRun "syncFoo"

        then:
        file("build/build-logic-1.0.jar.foo").exists()
    }
}

