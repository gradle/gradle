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

    def "can apply a plugin declared in a catalog using from"() {
        String message = "Problem: In version catalog libs, you can only call the 'from' method a single time."
        String filePath = "gradle/amal.libs.versions.toml"

        versionCatalogFile << """versions]
swaggerCoreVersion = "2.2.25"
springDocVersion = "1.8.0"
classGraphVersion = "4.8.112"

[libraries]
swaggercore = {group="io.swagger.core.v3", name="swagger-core", version.ref = "swaggerCoreVersion"}
swaggerannotations = {group="io.swagger.core.v3", name="swagger-annotations", version.ref = "swaggerCoreVersion"}
swaggerintegration = {group="io.swagger.core.v3", name="swagger-integration", version.ref = "swaggerCoreVersion"}
classgraph = {group="io.github.classgraph", name="classgraph", version.ref = "classGraphVersion"}
springdoc = {group="org.springdoc", name="springdoc-openapi-ui", version.ref = "springDocVersion"}"""


        def file = file(filePath)

        file.text = """[versions]
swaggerCoreVersion = "2.2.25"
springDocVersion = "1.8.0"
classGraphVersion = "4.8.112"

[libraries]
swaggercore = {group="io.swagger.core.v3", name="swagger-core", version.ref = "swaggerCoreVersion"}
swaggerannotations = {group="io.swagger.core.v3", name="swagger-annotations", version.ref = "swaggerCoreVersion"}
swaggerintegration = {group="io.swagger.core.v3", name="swagger-integration", version.ref = "swaggerCoreVersion"}
classgraph = {group="io.github.classgraph", name="classgraph", version.ref = "classGraphVersion"}
springdoc = {group="org.springdoc", name="springdoc-openapi-ui", version.ref = "springDocVersion"}
"""

        // We use the Groovy DSL for settings because that's not what we want to
        // test and the setup would be more complicated with Kotlin
        settingsKotlinFile << """
rootProject.name = "learn"

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("gradle/amal.libs.versions.toml"))
        }
    }
}"""
        buildKotlinFile << """
plugins {
\tjava
}

group = "com.amal"
version = "0.0.1-SNAPSHOT"

java {
\ttoolchain {
\t\tlanguageVersion = JavaLanguageVersion.of(17)
\t}
}

configurations {
\tcompileOnly {
\t\textendsFrom(configurations.annotationProcessor.get())
\t}
}

repositories {
\tmavenCentral()
}


tasks.withType<Test> {
\tuseJUnitPlatform()
}

"""

        when:
        def result = succeeds "build"

        then:
        def receivedResults = result.getOutput() // Capture the failure details
        verifyAll {
            receivedResults.contains("BUILD SUCCESSFUL")
        }
    }
}
