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

package org.gradle.integtests.resolve.gmm

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue

class GradleModuleMetadataResolveIntegrationTest extends AbstractIntegrationSpec {

    @Issue("https://github.com/gradle/gradle/issues/26468")
    def "can consume gmm with dependency null or empty artifactSelector extension"() {
        given:
        mavenRepo.module("org", "transitive", "1.0")
            .artifact(classifier: "cls")
            .artifact(classifier: "cls", type: "")
            .withNoPom()
            .withModuleMetadata()
            .publish()

        def module = mavenRepo.module("org", "foo")
        module.withNoPom().publish()
        module.artifactFile(type: "module").text = """
            {
                "formatVersion": "1.1",
                "component": {
                    "group": "org",
                    "module": "foo",
                    "version": "1.0",
                    "attributes": {
                        "org.gradle.status": "release"
                    }
                },
                "variants": [
                    {
                        "name": "runtime",
                        "dependencies": [
                            {
                                "group": "org",
                                "module": "transitive",
                                "version": {
                                    "requires": "1.0"
                                },
                                "thirdPartyCompatibility": {
                                    "artifactSelector": {
                                        "name": "transitive",
                                        "type": "jar",
                                        ${artifactDeclaration}
                                        "classifier": "cls"
                                    }
                                }
                            }
                        ],
                        "attributes": {
                            "org.gradle.usage": "java-runtime",
                            "org.gradle.libraryelements": "jar",
                            "org.gradle.category": "library"
                        },
                        "files": [
                            {
                                "name": "foo-1.0.jar",
                                "url": "foo-1.0.jar"
                            }
                        ]
                    }
                ]
            }
        """

        buildFile << """
            plugins {
                id("java-library")
            }

            repositories {
                maven {
                    url = "${mavenRepo.uri}"
                    metadataSources {
                        gradleMetadata()
                    }
                }
            }

            dependencies {
                implementation("org:foo:1.0")
            }

            task resolve {
                def files = configurations.runtimeClasspath
                doLast {
                    assert files*.name == ["foo-1.0.jar", "${expectedFile}"]
                }
            }
        """

        expect:
        succeeds("resolve")

        where:
        artifactDeclaration | expectedFile
        ""                  | "transitive-1.0-cls.jar"
        '"extension": "",'  | "transitive-1.0-cls"
    }
}
