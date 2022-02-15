/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.jvm

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class JavaLibraryOutgoingElementsBuilderIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        settingsFile << """
            rootProject.name = 'mylib'
        """
        buildFile << """
            plugins {
                id 'java-library'
                id 'maven-publish'
            }
            def jvm = extensions.create(org.gradle.api.plugins.jvm.internal.JvmPluginExtension, "jvm", org.gradle.api.plugins.jvm.internal.DefaultJvmPluginExtension)

            group = 'com.acme'
            version = '1.4'

            publishing {
                repositories {
                    maven {
                        url "\${buildDir}/repo"
                    }
                }
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }
        """
    }

    def "configures an additional outgoing variant (#scenario, #capability)"() {
        buildFile << """
            def shadowJar = tasks.register("shadowJar", Jar) {
                archiveClassifier = 'all'
                from(sourceSets.main.output)
            }

            def shadowElements = jvm.createOutgoingElements("shadowElements") {
                withDescription 'A fat jar'
                if ($runtime) {
                    providesRuntime()
                } else {
                    providesApi()
                }
                artifact(shadowJar)
                if ($published) {
                    published()
                }
                if ($cgroup) {
                    capability($cgroup, $cname, $cversion)
                }
            }
        """

        when:
        run 'outgoingVariants'

        then:
        outputContains """Variant shadowElements
--------------------------------------------------
A fat jar

Capabilities
    - $capability
Attributes
    - org.gradle.category            = library
    - org.gradle.dependency.bundling = external
    - org.gradle.libraryelements     = jar
    - org.gradle.usage               = java-${runtime ? 'runtime' : 'api'}
Artifacts
    - build${File.separator}libs${File.separator}mylib-1.4-all.jar (artifactType = jar, classifier = all)
"""
        when:
        run 'publish'

        then:
        if (published) {
            executed ':shadowJar'
        } else {
            notExecuted ':shadowJar'
        }

        where:
        scenario                   | published | runtime | cgroup  | cname     | cversion
        "with publishing"          | true      | true    | null    | null      | null
        "without publishing"       | false     | true    | null    | null      | null
        "non published API"        | false     | false   | null    | null      | null
        "with explicit capability" | false     | false   | "'com'" | "'other'" | "'1.2'"

        capability = cgroup == null ? 'com.acme:mylib:1.4 (default capability)' : "${cgroup}:${cname}:${cversion}".replaceAll(/'/, '')
    }

    def "can configure an additional outgoing variant from a source set (with classes dir=#classesDir)"() {
        buildFile << """
            sourceSets {
                integTest
            }

            jvm.createOutgoingElements("integTestElements") {
                fromSourceSet(sourceSets.integTest)
                providesApi()
                if ($classesDir) {
                    withClassDirectoryVariant()
                }
            }
        """

        when:
        run 'outgoingVariants'

        then:
        outputContains """Variant integTestElements
--------------------------------------------------

Capabilities
    - com.acme:mylib:1.4 (default capability)
Attributes
    - org.gradle.category            = library
    - org.gradle.dependency.bundling = external
    - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
    - org.gradle.libraryelements     = jar
    - org.gradle.usage               = java-api
"""
        if (classesDir) {
            outputContains """Variant integTestElements
--------------------------------------------------

Capabilities
    - com.acme:mylib:1.4 (default capability)
Attributes
    - org.gradle.category            = library
    - org.gradle.dependency.bundling = external
    - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
    - org.gradle.libraryelements     = jar
    - org.gradle.usage               = java-api

Secondary Variants (*)

    --------------------------------------------------
    Secondary Variant classes
    --------------------------------------------------
    Directories containing compiled class files for integTest.

    Attributes
        - org.gradle.category            = library
        - org.gradle.dependency.bundling = external
        - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
        - org.gradle.libraryelements     = classes
        - org.gradle.usage               = java-api
    Artifacts
        - build${File.separator}classes${File.separator}java${File.separator}integTest (artifactType = java-classes-directory)"""
        }

        where:
        classesDir << [false, true]
    }

    def "can configure an outgoing elements configuration for documentation"() {
        buildFile << """
            def userguide = tasks.register('userguide') {
                outputs.file('userguide.zip')
            }

            jvm.createOutgoingElements('userguide') {
                attributes {
                    documentation('userguide')
                }
                artifact(userguide)
            }
        """

        when:
        succeeds 'outgoingVariants'

        then:
        outputContains """Variant userguide
--------------------------------------------------

Capabilities
    - com.acme:mylib:1.4 (default capability)
Attributes
    - org.gradle.category            = documentation
    - org.gradle.dependency.bundling = external
    - org.gradle.docstype            = userguide
    - org.gradle.usage               = java-runtime
Artifacts
    - userguide.zip (artifactType = zip)"""
    }
}
