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

package org.gradle.integtests.resolve.transform

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache

class ReachingAcrossProjectBoundariesIntegrationTest extends AbstractDependencyResolutionTest implements ArtifactTransformTestFixture {
    // This tests current behaviour, not desired behaviour
    @UnsupportedWithConfigurationCache(because = "Task does not declare that it uses the transform outputs")
    def "can consume transform outputs produced by another project without declaring this access"() {
        settingsFile << """
            include 'a', 'b', 'sneaky'
        """
        setupBuildWithColorTransformImplementation()
        buildFile << """
            project(':a') {
                dependencies {
                    implementation project(':b')
                }
                task prepare {
                    inputs.files configurations.resolver
                    doFirst {
                        configurations.resolver.files.name
                    }
                }
            }
            def files = project(':a').tasks.resolveArtifacts.collection.artifactFiles
            project(':sneaky') {
                task sneaky {
                    // Need to make sure that the configuration in the other project has been resolved but the transforms have not run yet
                    dependsOn(":a:prepare")
                    doLast {
                        println("result = " + files.files.name)
                    }
                }
            }
        """

        when:
        run("sneaky:sneaky", "--parallel")

        then:
        outputContains("result = [b.jar.green]")
    }

}
