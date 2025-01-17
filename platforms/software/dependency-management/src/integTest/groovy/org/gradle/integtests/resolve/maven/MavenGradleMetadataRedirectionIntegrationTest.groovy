/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.integtests.resolve.maven

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import org.gradle.test.fixtures.server.http.MavenHttpModule

class MavenGradleMetadataRedirectionIntegrationTest extends AbstractHttpDependencyResolutionTest {
    MavenHttpModule mainModule
    MavenHttpModule dep
    ResolveTestFixture resolve

    def setup() {
        mainModule = mavenHttpRepo.module("org", "main", "1.0").withModuleMetadata()
        dep = mavenHttpRepo.module("org", "foo", "1.9").publish()
        settingsFile << "rootProject.name = 'test'"
        buildFile << """
            apply plugin: 'java-library'

            repositories {
                maven { url = "${mavenHttpRepo.uri}" }
            }
        """
        prepareResolution()
    }

    def "doesn't try to fetch Gradle metadata if published and marker is not present"() {
        given:
        createPomFile(false)

        buildFile << """
            dependencies {
                api "org:main:1.0"
            }
        """

        when:
        mainModule.pom.expectGet()
        mainModule.artifact.expectGet()
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module('org:main:1.0')
            }
        }
    }

    def "prefers Gradle metadata if published and marker is present"() {
        given:
        createPomFile(true)

        buildFile << """
            dependencies {
                api "org:main:1.0"
            }
        """

        when:
        mainModule.pom.expectGet()
        mainModule.moduleMetadata.expectGet()
        mainModule.artifact.expectGet()
        dep.pom.expectGet()
        dep.artifact.expectGet()

        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module('org:main:1.0') {
                    variant('api', ['org.gradle.status': 'release', 'org.gradle.usage': 'java-api', 'org.gradle.libraryelements': 'jar', 'org.gradle.category': 'library'])
                    edge('org:foo:{prefer 1.9}', 'org:foo:1.9')
                }
            }
        }
    }

    def "reasonable behavior when POM file says Gradle metadata is present but is not"() {
        given:
        createPomFile(true)

        buildFile << """
            dependencies {
                api "org:main:1.0"
            }
        """

        when:
        mainModule.pom.expectGet()
        mainModule.moduleMetadata.expectGetMissing()
        mainModule.artifact.expectGet()

        run ':checkDeps'

        then:
        // falls back to POM file, since we have one
        resolve.expectGraph {
            root(":", ":test:") {
                module('org:main:1.0')
            }
        }
    }

    def "doesn't try to fetch Gradle metadata if published has marker present and ignoreGradleMetadataRedirection is set"() {
        setup:
        buildFile.text = """
            apply plugin: 'java-library'

            repositories {
                maven {
                    url = "${mavenHttpRepo.uri}"
                    metadataSources {
                        mavenPom()
                        artifact()
                        ignoreGradleMetadataRedirection()
                    }
                }
            }

             dependencies {
                api "org:main:1.0"
            }
        """
        prepareResolution()
        createPomFile(true)

        when:
        mainModule.pom.expectGet()
        mainModule.artifact.expectGet()

        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module('org:main:1.0')
            }
        }
    }

    private void createPomFile(boolean marker) {
        if (!marker) {
            mainModule.withoutGradleMetadataRedirection()
        }
        mainModule.publish()
        // and now we manually patch the Gradle metadata so that its dependencies
        // are different. This is done so that we can check that it's really the
        // metadata issued from the Gradle metadata file which is used
        def moduleFile = mainModule.moduleMetadata.file
        moduleFile.replace('"dependencies":[]', '''"dependencies":[
            { "group": "org", "module": "foo", "version": { "prefers": "1.9" } }
        ]''')
    }

    private void prepareResolution() {
        resolve = new ResolveTestFixture(buildFile, "compileClasspath")
        resolve.expectDefaultConfiguration('compile')
        resolve.prepare()
    }
}
