/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.integtests.tooling

import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.IntermediateModelListener
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.eclipse.EclipseProject

import java.util.concurrent.CopyOnWriteArrayList

class IntermediateModelSendingBuildActionCrossVersionTest extends ToolingApiSpecification {

    def "can send intermediate models and then receive them in the same order"() {
        given:
        file("settings.gradle") << 'rootProject.name="hello-world"'

        when:
        Collection<Object> intermediateModels = new CopyOnWriteArrayList<Object>()
        CustomModel customModel = withConnection {
            def builder = it.action(new IntermediateModelSendingBuildAction())
            builder.setIntermediateModelListener({ model ->
                intermediateModels.add(model)
            } as IntermediateModelListener)
            builder.run()
        }

        then:
        customModel.value == 42

        and:
        intermediateModels.size() == 2

        and:
        GradleProject gradleProject = intermediateModels.get(0)
        gradleProject.name == "hello-world"

        and:
        EclipseProject eclipseModel = intermediateModels.get(1)
        eclipseModel.gradleProject.name == ""
    }

}
