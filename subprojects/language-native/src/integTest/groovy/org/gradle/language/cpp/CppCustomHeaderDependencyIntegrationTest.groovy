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

import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.app.CppApp

class CppCustomHeaderDependencyIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    def "can consume a directory as a header dependency"() {
        def app = new CppApp()
        settingsFile << """
            rootProject.name = "app"
        """

        buildFile << consumerBuildScript(repoType)

        file("lib/settings.gradle") << """
            enableFeaturePreview('STABLE_PUBLISHING')
        """

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
//            'ivy'
        ]
    }

    def consumerBuildScript(String repoType) {
        return """
            import javax.inject.Inject

            apply plugin: 'cpp-application'
            
            repositories {
                ${repoType} {
                    url file('lib/repo')
                }
            }
            
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
                
                registerTransform {
                    from.attribute(USAGE, CUSTOM)
                    to.attribute(USAGE, C_PLUS_PLUS_API)
                    artifactTransform(UnzipTransform) {
                        params(file('lib/src/main/headers'))
                    }
                }
                
                registerTransform {
                    from.attribute(USAGE, CUSTOM)
                    to.attribute(USAGE, NATIVE_RUNTIME)
                    artifactTransform(EmptyTransform)
                }
                
                registerTransform {
                    from.attribute(USAGE, CUSTOM)
                    to.attribute(USAGE, NATIVE_LINK)
                    artifactTransform(EmptyTransform)
                }
            }
            
            // Simulates unzipping headers by copying the contents of a configured directory
            // This is to avoid pulling in an external dependency to do this or investing the 
            // effort of writing our own unzip which would be pure yak-shaving for this test.
            class UnzipTransform extends ArtifactTransform {
                File headerDir
                
                @Inject
                UnzipTransform(File headerDir) {
                   this.headerDir = headerDir
                }
                
                List<File> transform(File file) {
                    def unzipped = new File(outputDirectory, "unzipped")
                    unzipped.mkdirs()
                    headerDir.listFiles().each { sourceFile ->
                        def headerFile = new File(unzipped, sourceFile.name)
                        headerFile.text = sourceFile.text
                    }
                    return [unzipped]
                }
            }
            
            class EmptyTransform extends ArtifactTransform {
                List<File> transform(File file) {
                    return []
                }
            }
        """
    }

    def producerBuildScript(String repoType) {
        return """
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
