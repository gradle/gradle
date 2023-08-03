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

package org.gradle.api.publish.ivy

import org.gradle.test.fixtures.file.TestFile

import javax.xml.namespace.QName

class IvyPublishDescriptorCustomizationKotlinDslIntegTest extends AbstractIvyPublishIntegTest {

    @Override
    protected String getDefaultBuildFileName() {
        'build.gradle.kts'
    }

    @Override
    protected TestFile getSettingsFile() {
        testDirectory.file('settings.gradle.kts')
    }

    def setup() {
        requireOwnGradleUserHomeDir() // Isolate Kotlin DSL extensions API jar
    }

    def "can customize Ivy descriptor using Kotlin DSL"() {
        given:
        settingsFile << 'rootProject.name = "customizeIvy"'
        buildFile << """
            plugins {
                `ivy-publish`
            }

            group = "org.gradle.test"
            version = "1.0"

            publishing {
                repositories {
                    ivy(url = "${ivyRepo.uri}")
                }
                publications {
                    create<IvyPublication>("mavenCustom") {
                        descriptor {
                            status = "custom-status"
                            branch = "custom-branch"
                            license {
                                name.set("The Apache License, Version 2.0")
                                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                            }
                            author {
                                name.set("Jane Doe")
                                url.set("http://example.com/users/jane")
                            }
                            description {
                                text.set("A concise description of my library")
                                homepage.set("http://www.example.com/library")
                            }
                            extraInfo("http://my.extra.info1", "foo", "fooValue")
                            extraInfo("http://my.extra.info2", "bar", "barValue")
                        }
                    }
                }
            }
        """

        when:
        succeeds 'publish'

        then:
        def module = ivyRepo.module("org.gradle.test", "customizeIvy", "1.0")
        with (module.parsedIvy) {
            status == "custom-status"
            branch == "custom-branch"
            licenses.size() == 1
            licenses[0].@name == 'The Apache License, Version 2.0'
            licenses[0].@url == 'http://www.apache.org/licenses/LICENSE-2.0.txt'
            authors.size() == 1
            authors[0].@name == 'Jane Doe'
            authors[0].@url == 'http://example.com/users/jane'
            description.text() == "A concise description of my library"
            description.@homepage == 'http://www.example.com/library'
            extraInfo.size() == 2
            extraInfo[new QName('http://my.extra.info1', 'foo')] == 'fooValue'
            extraInfo[new QName('http://my.extra.info2', 'bar')] == 'barValue'
        }
    }
}
