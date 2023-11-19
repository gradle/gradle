/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.integtests.tooling.r86

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.IntermediateModelListener
import org.gradle.tooling.IntermediateResultHandler
import org.gradle.tooling.ResultHandler
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.eclipse.EclipseProject

import java.util.concurrent.CopyOnWriteArrayList

@ToolingApiVersion(">=8.6")
@TargetGradleVersion(">=8.6")
class IntermediateModelSendingBuildActionCrossVersionTest extends ToolingApiSpecification {

    def "build action can send intermediate models and then receive them in the same order"() {
        given:
        file("settings.gradle") << 'rootProject.name="hello-world"'

        when:
        def models = new CopyOnWriteArrayList<Object>()
        def listener = { model -> models.add(model) } as IntermediateModelListener
        def handler = { model -> models.add(model) } as ResultHandler

        withConnection {
            def builder = it.action(new IntermediateModelSendingBuildAction())
            collectOutputs(builder)
            builder.setIntermediateModelListener(listener)
            builder.run(handler)
        }

        then:
        models.size() == 3

        and:
        GradleProject gradleProject = models.get(0)
        gradleProject.name == "hello-world"

        and:
        EclipseProject eclipseModel = models.get(1)
        eclipseModel.gradleProject.name == "hello-world"

        and:
        CustomModel result = models.get(2)
        result.value == 42
    }

    def "phased build action can send intermediate models and then receive them in the same order"() {
        given:
        file("settings.gradle") << 'rootProject.name="hello-world"'

        when:
        def models = new CopyOnWriteArrayList<Object>()
        def listener = { model -> models.add(model) } as IntermediateModelListener
        def handler = { model -> models.add(model) } as IntermediateResultHandler

        withConnection {
            def builder = it.action()
                .projectsLoaded(new CustomIntermediateModelSendingBuildAction(GradleProject, 1), handler)
                .buildFinished(new CustomIntermediateModelSendingBuildAction(EclipseProject, 2), handler)
                .build()
            collectOutputs(builder)
            builder.setIntermediateModelListener(listener)
            builder.run()
        }

        then:
        models.size() == 4

        and:
        CustomModel model1 = models.get(0)
        model1.value == 1

        and:
        GradleProject gradleProject = models.get(1)
        gradleProject.name == "hello-world"

        and:
        CustomModel model2 = models.get(2)
        model2.value == 2

        and:
        EclipseProject eclipseModel = models.get(3)
        eclipseModel.gradleProject.name == "hello-world"
    }
}
