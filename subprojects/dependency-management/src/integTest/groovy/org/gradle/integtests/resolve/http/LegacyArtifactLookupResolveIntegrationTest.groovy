/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.integtests.resolve.http

import org.gradle.integtests.fixtures.GradleMetadataResolveRunner
import org.gradle.integtests.resolve.AbstractModuleDependencyResolveTest
import org.gradle.test.fixtures.ivy.IvyModule
import org.gradle.test.fixtures.maven.MavenModule

class LegacyArtifactLookupResolveIntegrationTest extends AbstractModuleDependencyResolveTest {
    def "tries to fetch the jar whenever the metadata artifact isn't found"() {
        buildFile << """
            dependencies {
                conf 'org:notfound:1.0'
            }
        """

        when:
        repositoryInteractions {
            'org:notfound:1.0' {
                expectGetMetadataMissing()
                if (GradleMetadataResolveRunner.gradleMetadataEnabled) {
                    // this is because the default is to look for Gradle first, then Ivy
                    withModule(IvyModule) {
                        ivy.expectGetMissing()
                    }
                    withModule(MavenModule) {
                        pom.expectGetMissing()
                    }
                }
                expectHeadArtifact()
            }
        }

        then:
        fails 'checkDeps'
    }

    def "does not try to fetch the jar whenever the metadata artifact isn't found and legacy mode is disabled"() {
        def source = GradleMetadataResolveRunner.useIvy() ? 'ivyDescriptor' : 'mavenPom'
        buildFile << """
            repositories.all {
                metadataSources {
                    gradleMetadata()
                    ${source}()
                }
            }

            dependencies {
                conf 'org:notfound:1.0'
            }
        """

        when:
        repositoryInteractions {
            'org:notfound:1.0' {
                expectGetMetadataMissing()
                if (GradleMetadataResolveRunner.gradleMetadataEnabled) {
                    // this is because we configured a 2d source
                    withModule(IvyModule) {
                        ivy.expectGetMissing()
                    }
                    withModule(MavenModule) {
                        pom.expectGetMissing()
                    }
                }
            }
        }

        then:
        fails 'checkDeps'
    }
}
