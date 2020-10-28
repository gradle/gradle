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

package org.gradle.platform

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

import static org.gradle.util.TextUtil.normaliseLineSeparators

class GradlePlatformIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        settingsFile << """
            rootProject.name = 'test'
        """
        buildFile << """
            plugins {
                id 'gradle-platform'
            }

            group = 'org.gradle'
            version = '1.0'
        """
    }

    def "can generate a Gradle platform file"() {
        withSamplePlatform()

        when:
        succeeds ':generatePlatformToml'

        then:
        expectPlatformContents 'expected1'
    }

    def "can publish a Gradle platform"() {
        withSamplePlatform()
        buildFile << """
            apply plugin: 'maven-publish'

            publishing {
                repositories {
                    maven {
                        url "$mavenRepo.uri"
                    }
                }
                publications {
                    maven(MavenPublication) {
                        from components.gradlePlatform
                    }
                }
            }
        """

        when:
        succeeds ':publish'

        then:
        executedAndNotSkipped ':generatePlatformToml',
            ':generateMetadataFileForMavenPublication',
            ':generatePomFileForMavenPublication',
            ':publishMavenPublicationToMavenRepository'
        def module = mavenRepo.module("org.gradle", "test", "1.0")
            .withModuleMetadata()
        module.assertPublished()
        def metadata = module.parsedModuleMetadata
        metadata.variant("gradlePlatformElements") {
            noMoreDependencies()
            assert attributes == [
                'org.gradle.category': 'platform',
                'org.gradle.usage': 'gradle-recommendations'
            ]
            assert files.name == ['test-1.0.toml']
        }
    }

    private void withSamplePlatform() {
        buildFile << """
            gradlePlatform {
                dependenciesModel {
                    alias("my-lib", "org:foo:1.0")
                    alias("junit4", "junit", "junit") {
                        require "[4.13.1, 5["
                        prefer "4.13.1"
                    }
                    version("lib", "1.1")
                    aliasWithVersionRef("other", "org", "bar", "lib")
                    bundle("test", ["my-lib", "junit4"])
                }
                plugins {
                    id("my.awesome.plugin") version "1.5"
                }
            }
        """
    }

    private void expectPlatformContents(File expectedTomlFile = file("build/gradle-platform/dependencies.toml"), String resultFile) {
        assert expectedTomlFile.exists()
        def generated = normaliseLineSeparators(expectedTomlFile.getText("utf-8"))
        def expected = normaliseLineSeparators(this.class.getResourceAsStream("${resultFile}.toml").getText("utf-8"))
        assert generated == expected
    }
}
