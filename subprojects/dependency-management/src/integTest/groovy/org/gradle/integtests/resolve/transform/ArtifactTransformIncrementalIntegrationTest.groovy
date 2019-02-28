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

package org.gradle.integtests.resolve.transform

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest

class ArtifactTransformIncrementalIntegrationTest extends AbstractDependencyResolutionTest implements ArtifactTransformTestFixture {

    def "can query incremental changes"() {
        settingsFile << """
            include 'a', 'b', 'c'
        """
        setupBuildWithColorTransform {
            produceDirs()
        }
        buildFile << """
            project(':a') {
                dependencies {
                    implementation project(':b')
                    implementation project(':c')
                }
            }
            
            abstract class MakeGreen implements TransformAction<TransformParameters.None> {

                @Inject
                abstract IncrementalInputs getIncrementalInputs()
                
                @InputArtifact
                abstract File getInput()
            
                void transform(TransformOutputs outputs) {
                    println "incremental: " + incrementalInputs.incremental
                    println "changes: \\n" + incrementalInputs.getChanges(input).join("\\n")
                }
            }
        """

        when:
        run(":a:resolve")
        then:
        outputContains("incremental: false")

        when:
        executer.withArguments("-PbContent=changed")
        run(":a:resolve")
        then:
        outputContains("incremental: true")
    }

}
