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

package org.gradle.api.plugins.quality.pmd

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.test.fixtures.server.http.MavenHttpModule

import static org.gradle.integtests.fixtures.RepoScriptBlockUtil.mavenCentralRepositoryDefinition

class PmdAuxClasspathAttributesIntegrationTest extends AbstractHttpDependencyResolutionTest {
    MavenHttpModule module

    def setup() {
        settingsFile << """
            rootProject.name = 'test'
        """

        buildFile << """
            plugins {
                id("java-library")
                id("pmd")
            }
            repositories {
                maven { url = '${mavenHttpRepo.uri}' }
                ${mavenCentralRepositoryDefinition()}
            }
            dependencies {
                pmdAux 'org:producer:1.0'
            }
            afterEvaluate {
                configurations.configureEach {
                    // use configureEach because the mainPmdAuxClasspath configuration does not exist at configuration time
                    attributes.attribute(Attribute.of('custom.attribute.1', String), '1')
                    attributes.attribute(Attribute.of('custom.attribute.2', String), '2')
                }
            }
        """

        module = mavenHttpRepo.module('org', 'producer', '1.0')
            .withModuleMetadata()
            .adhocVariants()
            .variant("runtimeElements", [
                'org.gradle.dependency.bundling': 'external',
                'org.gradle.jvm.version': 25,
                'org.gradle.category': 'library',
                'org.gradle.libraryelements': 'jar',
                'org.gradle.usage': 'java-runtime',
                'custom.attribute.1': '1',
                'custom.attribute.2': '2'
            ], { artifact('producer-1.0.jar') })
            .variant("runtimeElementsCustom3a", [
                'org.gradle.dependency.bundling': 'external',
                'org.gradle.jvm.version': 25,
                'org.gradle.category': 'library',
                'org.gradle.libraryelements': 'jar',
                'org.gradle.usage': 'java-runtime',
                'custom.attribute.1': '1',
                'custom.attribute.2': '2',
                'custom.attribute.3': 'a'
            ], { artifact('producer-1.0-custom.jar') })
            .variant("runtimeElementsCustom3b", [
                'org.gradle.dependency.bundling': 'external',
                'org.gradle.jvm.version': 25,
                'org.gradle.category': 'library',
                'org.gradle.libraryelements': 'jar',
                'org.gradle.usage': 'java-runtime',
                'custom.attribute.1': '1',
                'custom.attribute.2': '2',
                'custom.attribute.3': 'b'
            ], { artifact('producer-1.0-custom.jar') })
            .publish()

    }

    def "pmd Aux classpath has enough attributes to disambiguate variants"() {
        given:
        module.pom.expectGet()
        module.moduleMetadata.expectGet()

        when:
        succeeds 'pmdMain', 'dependencyInsight', '--configuration', 'mainPmdAuxClasspath', '--dependency', 'producer'

        then:
        // without the correct attributes, the build will fail with "Could not resolve org:producer:1.0" and "Ambiguity errors"
        outputContains('org:producer:1.0')
        outputContains('Variant runtimeElements:')
        outputDoesNotContain('FAILED')
    }
}
