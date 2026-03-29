/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.publish.maven

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class MavenPublishPomVariantIntegTest extends AbstractIntegrationSpec {

    def "can resolve POM variant from project dependency with maven-publish"() {
        settingsFile << """
            include 'lib', 'consumer'
        """

        file('lib/build.gradle') << """
            plugins {
                id 'java-library'
                id 'maven-publish'
            }

            group = 'com.example'
            version = '1.0'

            publishing {
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }
        """

        file('consumer/build.gradle') << """
            plugins {
                id 'java-library'
            }

            dependencies {
                implementation project(':lib')
            }

            task resolvePom {
                def pomFiles = configurations.runtimeClasspath.incoming.artifactView {
                    withVariantReselection()
                    attributes {
                        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, Category.METADATA))
                        attribute(MetadataFormat.METADATA_FORMAT_ATTRIBUTE, objects.named(MetadataFormat, MetadataFormat.MAVEN))
                    }
                }.files
                doLast {
                    def names = pomFiles*.name
                    assert names.size() == 1 : "Expected 1 POM file but got: \${names}"
                    assert names[0].endsWith('.xml') || names[0].endsWith('.pom') : "Expected POM file but got: \${names[0]}"
                }
            }
        """

        expect:
        succeeds(':consumer:resolvePom')
    }
}
