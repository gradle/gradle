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

package org.gradle.integtests.resolve.catalog

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.test.fixtures.server.http.MavenHttpPluginRepository
import org.junit.Rule

@LeaksFileHandles("Kotlin Compiler Daemon working directory")
class DefaultVersionCatalogIntegrationTest extends AbstractIntegrationSpec {
    @Rule
    final MavenHttpPluginRepository pluginPortal = MavenHttpPluginRepository.asGradlePluginPortal(executer, mavenRepo)

    private String versionCatalogSampleText = """[versions]
            swaggerCoreVersion = "2.2.25"
            springDocVersion = "1.8.0"
            classGraphVersion = "4.8.112"

            [libraries]
            swaggercore = {group="io.swagger.core.v3", name="swagger-core", version.ref = "swaggerCoreVersion"}
            swaggerannotations = {group="io.swagger.core.v3", name="swagger-annotations", version.ref = "swaggerCoreVersion"}
            swaggerintegration = {group="io.swagger.core.v3", name="swagger-integration", version.ref = "swaggerCoreVersion"}
            classgraph = {group="io.github.classgraph", name="classgraph", version.ref = "classGraphVersion"}
            springdoc = {group="org.springdoc", name="springdoc-openapi-ui", version.ref = "springDocVersion"}"""

    def "can apply a custom version catalog using from"() {
        String message = "BUILD SUCCESSFUL"
        String filePath = "gradle/custom.libs.versions.toml"

        versionCatalogFile << versionCatalogSampleText


        def file = file(filePath)

        file.text = versionCatalogSampleText

        settingsKotlinFile << """
            rootProject.name = "learn"

            dependencyResolutionManagement {
                versionCatalogs {
                    create("libs") {
                        from(files("gradle/custom.libs.versions.toml"))
                    }
                }
            }"""
        when:
        def result = succeeds "build"

        then:
        def receivedResults = result.getOutput()
        verifyAll {
            receivedResults.contains(message)
        }
    }

    def "fails when calling from on default version catalog explicitly"() {
        String message = "In version catalog libs, you can only import default catalog `gradle/libs.versions.toml` once."
        String filePath = "gradle/libs.versions.toml"

        versionCatalogFile << versionCatalogSampleText

        def file = file(filePath)

        file.text = versionCatalogSampleText

        settingsKotlinFile << """
            rootProject.name = "learn"

            dependencyResolutionManagement {
                versionCatalogs {
                    create("libs") {
                        from(files("gradle/libs.versions.toml"))
                    }
                }
            }"""

        when:
        def result = fails "build"

        then:
        def receivedResults = result.getError()
        verifyAll {
            receivedResults.contains(message)
        }
    }

    def "fails when calling from twice on the same custom catalog file"() {
        String message = "When defining version catalog 'custom', you can only call the 'from' method a single time."
        def customToml = file("gradle/custom.versions.toml")
        customToml.text = """
        [versions]
        someVersion = "1.0.0"

        [libraries]
        someLib = { group = "com.example", name = "lib", version.ref = "someVersion" }
        """

            settingsKotlinFile << """
        rootProject.name = "learn"

        dependencyResolutionManagement {
            versionCatalogs {
                create("custom") {
                    from(files("gradle/custom.versions.toml"))
                    from(files("gradle/custom.versions.toml")) // Second call should fail
                }
            }
        }
        """
        when:
        def result = fails "build"

        then:
        def receivedResults = result.getError()
        verifyAll {
            receivedResults.contains(message)
        }
    }

    def "fails when calling from twice on the different custom catalog file"() {
        String message = "In version catalog custom, you can only call the 'from' method a single time."
        def customToml1 = file("gradle/custom1.versions.toml")
        customToml1.text = """
        [versions]
        someVersion = "1.0.0"

        [libraries]
        someLib = { group = "com.example", name = "lib", version.ref = "someVersion" }
        """

        def customToml2 = file("gradle/custom2.versions.toml")
        customToml2.text = """
        [versions]
        someVersion = "1.0.0"

        [libraries]
        someLib = { group = "com.example", name = "lib", version.ref = "someVersion" }
        """

        settingsKotlinFile << """
        rootProject.name = "learn"

        dependencyResolutionManagement {
            versionCatalogs {
                create("custom") {
                    from(files("gradle/custom1.versions.toml"))
                    from(files("gradle/custom2.versions.toml")) // Second call should fail
                }
            }
        }
        """
        when:
        def result = fails "build"

        then:
        def receivedResults = result.getError()
        verifyAll {
            receivedResults.contains(message)
        }
    }

    def "can use custom catalog with same name as default from subdirectory"() {
        String message = "BUILD SUCCESSFUL"

        def customToml = file("somesubdir/gradle/libs.versions.toml")
        customToml.text = """
        [versions]
        someVersion = "1.0.0"

        [libraries]
        someLib = { group = "com.example", name = "lib", version.ref = "someVersion" }
        """

        settingsKotlinFile << """
        rootProject.name = "learn"

        dependencyResolutionManagement {
            versionCatalogs {
                create("custom") {
                    // This should work, as it's explicitly pointing to a non-default location
                    from(files("somesubdir/gradle/libs.versions.toml"))
                }
            }
        }
         """

        when:
        def result = succeeds "build"

        then:
        def receivedResults = result.getOutput()
        verifyAll {
            receivedResults.contains(message)
        }
    }
}
