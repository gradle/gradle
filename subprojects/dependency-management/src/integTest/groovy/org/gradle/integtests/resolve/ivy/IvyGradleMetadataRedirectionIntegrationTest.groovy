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

package org.gradle.integtests.resolve.ivy

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import org.gradle.test.fixtures.server.http.IvyHttpModule

class IvyGradleMetadataRedirectionIntegrationTest extends AbstractHttpDependencyResolutionTest {
    IvyHttpModule mainModule
    IvyHttpModule dep
    ResolveTestFixture resolve

    def setup() {
        mainModule = ivyHttpRepo.module("org", "main", "1.0").withModuleMetadata()
        dep = ivyHttpRepo.module("org", "foo", "1.9").publish()
        settingsFile << "rootProject.name = 'test'"
        buildFile << """
            apply plugin: 'java-library'

            repositories {
                ivy { url "${ivyHttpRepo.uri}" }
            }
        """
        prepareResolution()

    }

    def "doesn't try to fetch Gradle metadata if published and marker is not present"() {
        given:
        createIvyFile(false)

        buildFile << """
            dependencies {
                api "org:main:1.0"
            }
        """

        when:
        mainModule.ivy.expectGet()
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
        createIvyFile(true)

        buildFile << """
            dependencies {
                api "org:main:1.0"
            }
        """

        when:
        mainModule.ivy.expectGet()
        mainModule.moduleMetadata.expectGet()
        mainModule.artifact.expectGet()
        dep.ivy.expectGet()
        dep.artifact.expectGet()

        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module('org:main:1.0') {
                    variant('api', ['org.gradle.status': 'integration', 'org.gradle.usage': 'java-api', 'org.gradle.libraryelements': 'jar', 'org.gradle.category': 'library'])
                    edge('org:foo:{prefer 1.9}', 'org:foo:1.9')
                }
            }
        }
    }

    def "reasonable behavior when Ivy file says Gradle metadata is present but is not"() {
        given:
        createIvyFile(true)

        buildFile << """
            dependencies {
                api "org:main:1.0"
            }
        """

        when:
        mainModule.ivy.expectGet()
        mainModule.moduleMetadata.expectGetMissing()
        mainModule.artifact.expectGet()

        run ':checkDeps'

        then:
        // falls back to Ivy file, since we have one
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
                ivy {
                    url "${ivyHttpRepo.uri}"
                    metadataSources {
                        ivyDescriptor()
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

        createIvyFile(true)

        when:
        mainModule.ivy.expectGet()
        mainModule.artifact.expectGet()

        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module('org:main:1.0')
            }
        }
    }

    private void createIvyFile(boolean marker) {
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
        resolve.prepare()
    }

}
