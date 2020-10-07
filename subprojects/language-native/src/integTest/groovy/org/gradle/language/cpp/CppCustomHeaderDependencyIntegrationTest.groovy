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

package org.gradle.language.cpp

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.app.CppApp

class CppCustomHeaderDependencyIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    @ToBeFixedForConfigurationCache
    def "can consume a directory as a header dependency"() {
        def app = new CppApp()
        settingsFile << """
            rootProject.name = "app"
        """

        buildFile << consumerBuildScript(repoType)

        file("lib/settings.gradle").touch()

        file("lib/build.gradle") << producerBuildScript(repoType)

        app.sources.writeToProject(testDirectory)
        app.headers.writeToProject(file('lib'))

        expect:
        projectDir('lib').withTasks('publish').run()

        and:
        succeeds("assemble")

        where:
        repoType << [
            'maven',
            'ivy'
        ]
    }

    def consumerBuildScript(String repoType) {
        return """
            apply plugin: 'cpp-application'

            repositories {
                ${repoType} {
                    url file('lib/repo')
                }
            }

            def artifactType = Attribute.of('artifactType', String)
            def USAGE = Usage.USAGE_ATTRIBUTE
            def CUSTOM = objects.named(Usage.class, "custom")
            def C_PLUS_PLUS_API = objects.named(Usage.class, Usage.C_PLUS_PLUS_API)
            def NATIVE_RUNTIME = objects.named(Usage.class, Usage.NATIVE_RUNTIME)
            def NATIVE_LINK = objects.named(Usage.class, Usage.NATIVE_LINK)
            dependencies {
                implementation "org.gradle.test:lib:1.0"

                artifactTypes {
                    zip {
                        attributes.attribute(USAGE, CUSTOM)
                    }
                }

                registerTransform(UnzipTransform) {
                    from.attribute(USAGE, CUSTOM).attribute(artifactType, 'zip')
                    to.attribute(USAGE, C_PLUS_PLUS_API).attribute(artifactType, 'directory')
                    parameters {
                        headerDir = file('lib/src/main/headers')
                    }
                }

                registerTransform(EmptyTransform) {
                    from.attribute(USAGE, CUSTOM).attribute(artifactType, 'zip')
                    to.attribute(USAGE, NATIVE_RUNTIME).attribute(artifactType, 'directory')
                }

                registerTransform(EmptyTransform) {
                    from.attribute(USAGE, CUSTOM).attribute(artifactType, 'zip')
                    to.attribute(USAGE, NATIVE_LINK).attribute(artifactType, 'directory')
                }
            }

            // Simulates unzipping headers by copying the contents of a configured directory
            // This is to avoid pulling in an external dependency to do this or investing the
            // effort of writing our own unzip which would be pure yak-shaving for this test.
            import org.gradle.api.artifacts.transform.TransformParameters

            abstract class UnzipTransform implements TransformAction<Parameters> {
                interface Parameters extends TransformParameters {
                    @InputDirectory
                    DirectoryProperty getHeaderDir()
                }

                void transform(TransformOutputs outputs) {
                    def unzipped = outputs.dir("unzipped")
                    parameters.headerDir.get().asFile.listFiles().each { sourceFile ->
                        def headerFile = new File(unzipped, sourceFile.name)
                        headerFile.text = sourceFile.text
                    }
                }
            }

            abstract class EmptyTransform implements TransformAction<TransformParameters.None> {
                void transform(TransformOutputs outputs) {
                }
            }
        """
    }

    def producerBuildScript(String repoType) {
        return """
            apply plugin: "base"
            apply plugin: "${repoType}-publish"

            configurations {
                headers
            }

            task zipHeaders(type: Zip) {
                from file('src/main/headers')
            }

            publishing {
                publications {
                    headers(${repoType.capitalize()}Publication) {
                        artifact zipHeaders
                        ${group(repoType)} = "org.gradle.test"
                        ${version(repoType)} = "1.0"
                    }
                }
                repositories {
                    ${repoType} {
                        url file('repo')
                    }
                }
            }
        """
    }

    def group(String repoType) {
        if (repoType == "ivy") {
            return "organisation"
        } else if (repoType == "maven") {
            return "groupId"
        } else {
            throw new IllegalArgumentException()
        }
    }

    def version(String repoType) {
        if (repoType == "ivy") {
            return "revision"
        } else if (repoType == "maven") {
            return "version"
        } else {
            throw new IllegalArgumentException()
        }
    }
}
