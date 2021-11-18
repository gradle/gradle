/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.plugins

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class PublishingVariantsIntegTest extends AbstractIntegrationSpec {
    def "publishing variants with duplicate names fails"() {
        given:
        settingsFile << "rootProject.name = 'lib'"

        buildFile << """
            plugins {
                id 'java'
                id 'maven-publish'
            }

            repositories {
                mavenLocal()
            }

            //def conf1 = configurations.create('sample1')
            //def conf2 = configurations.create('sample1')

            //components.java.withVariantsFromConfiguration(conf1) {
            //    skip()
            //}
            //components.java.withVariantsFromConfiguration(conf2)

            publishing {
                publications {
                    mavenJava(MavenPublication) {
                        from components.java
                    }
                }
            }

            group 'org.sample.SampleLib'
            """.stripIndent()

        file("src/main/java/org/sample/SampleLib.java") << """
            public class SampleLib {
                public void foo() {}
            }
            """.stripIndent()

        when:
        succeeds("outgoingVariants", "publishToMavenLocal")

        then:
        outputContains("Variant")
    }

    def "test variant guarantees"() {
        given:
        settingsFile << "rootProject.name = 'lib'"

        buildFile << """
            plugins {
                id 'java'
                id 'maven-publish'
            }

            repositories {
                mavenLocal()
            }

            publishing {
                publications {
                    mavenJava(MavenPublication) {
                        from components.java
                    }
                }
            }

            group 'org.sample.SampleLib'
            """.stripIndent()

        file("src/main/java/org/sample/SampleLib.java") << """
            public class SampleLib {
                public void foo() {}
            }
            """.stripIndent()

        expect:
        succeeds("build", "publishToMavenLocal")
    }
}
