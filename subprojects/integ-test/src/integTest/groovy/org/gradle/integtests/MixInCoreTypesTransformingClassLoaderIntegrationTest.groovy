/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.integtests

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class MixInCoreTypesTransformingClassLoaderIntegrationTest extends AbstractIntegrationSpec {
    def "custom task types have bridge methods for inputs/outputs"() {
        buildFile << """
            import org.gradle.api.internal.*
            import org.gradle.api.internal.tasks.*

            class CustomTask extends DefaultTask {}

            class Assert {
                static def returnTypesFor(Object object, String methodName, Class... returnTypes) {
                    assert object.getClass().methods.findAll { it.name == methodName }*.returnType.name.sort() == returnTypes*.name.sort()
                }
            }

            task customTask(type: CustomTask) {
                doFirst { task ->
                    // Internal return type leaked in Gradle 0.9
                    Assert.returnTypesFor task, "getOutputs", TaskOutputs, TaskOutputsInternal

                    // Internal return type leaked in Gradle 3.2
                    Assert.returnTypesFor task, "getInputs", TaskInputs, TaskInputsInternal

                    // Internal return types leaked in Gradle 3.2
                    Assert.returnTypesFor task.inputs, "dir", TaskInputFilePropertyBuilder, TaskInputFilePropertyBuilderInternal
                    Assert.returnTypesFor task.inputs, "file", TaskInputFilePropertyBuilder, TaskInputFilePropertyBuilderInternal
                    Assert.returnTypesFor task.inputs, "files", TaskInputFilePropertyBuilder, TaskInputFilePropertyBuilderInternal
                }
            }
        """

        expect:
        succeeds "customTask"
    }
}
