/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.cc.impl.isolated

import org.gradle.internal.cc.impl.fixtures.CustomModel
import org.gradle.internal.cc.impl.fixtures.SomeToolingModel
import org.gradle.tooling.StreamedValueListener
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.eclipse.EclipseProject

import java.util.concurrent.CopyOnWriteArrayList

class IsolatedProjectsToolingApiStreamingBuildActionIntegrationTest extends AbstractIsolatedProjectsToolingApiIntegrationTest {

    def setup() {
        file("settings.gradle") << 'rootProject.name="hello-world"'
    }

    def "models are streamed on build action cache hit"() {
        def listener1 = new TestStreamedValueListener()
        def listener2 = new TestStreamedValueListener()

        when:
        withIsolatedProjects()
        def model = runBuildAction(new ModelStreamingBuildAction()) {
            setStreamedValueListener(listener1)
        }

        then:
        fixture.assertModelStored {
            projectConfigured(":")
            modelsCreated(":", 3)
        }

        and:
        model.value == 42

        and:
        def streamedModels = listener1.models
        streamedModels.size() == 2
        (streamedModels[0] as GradleProject).name == "hello-world"
        (streamedModels[1] as EclipseProject).gradleProject.name == "hello-world"

        when:
        withIsolatedProjects()
        def model2 = runBuildAction(new ModelStreamingBuildAction()) {
            setStreamedValueListener(listener2)
        }

        then:
        fixture.assertModelLoaded()

        and:
        model2.value == 42

        and:
        def streamedModels2 = listener2.models
        streamedModels2.size() == 2
        (streamedModels2[0] as GradleProject).name == "hello-world"
        (streamedModels2[1] as EclipseProject).gradleProject.name == "hello-world"
    }

    def "models are streamed on phased build action cache hit"() {
        def listener1 = new TestStreamedValueListener()
        def listener2 = new TestStreamedValueListener()

        when:
        withIsolatedProjects()
        def model = runPhasedBuildAction(new CustomModelStreamingBuildAction(GradleProject, 1), new CustomModelStreamingBuildAction(EclipseProject, 2)) {
            setStreamedValueListener(listener1)
        }

        then:
        fixture.assertModelStored {
            projectConfigured(":")
            modelsCreated(":", 3)
        }

        and:
        (model.left as GradleProject).name == "hello-world"
        (model.right as EclipseProject).gradleProject.name == "hello-world"

        and:
        def streamedModels = listener1.models as List<CustomModel>
        streamedModels.size() == 2
        streamedModels[0].value == 1
        streamedModels[1].value == 2


        when:
        withIsolatedProjects()
        def model2 = runPhasedBuildAction(new CustomModelStreamingBuildAction(GradleProject, 1), new CustomModelStreamingBuildAction(EclipseProject, 2)) {
            setStreamedValueListener(listener2)
        }

        then:
        fixture.assertModelLoaded()

        and:
        (model2.left as GradleProject).name == "hello-world"
        (model2.right as EclipseProject).gradleProject.name == "hello-world"

        and:
        def streamedModels2 = listener2.models as List<CustomModel>
        streamedModels2.size() == 2
        streamedModels2[0].value == 1
        streamedModels2[1].value == 2
    }

    def "models are streamed on build action partial cache hit"() {
        withSomeToolingModelBuilderPluginInBuildSrc()
        settingsFile """
            include("a")
            include("b")
        """
        buildFile "a/build.gradle", """
            plugins.apply(my.MyPlugin)
        """
        buildFile "b/build.gradle", """
            plugins.apply(my.MyPlugin)
        """

        def listener = new TestStreamedValueListener()

        when:
        withIsolatedProjects()
        def messages1 = runBuildAction(new StreamCustomModelForEachProject()) {
            setStreamedValueListener(listener)
        }

        then:
        fixture.assertModelStored {
            projectConfigured(":buildSrc")
            buildModelCreated()
            projectConfigured(":")
            modelsCreated(":a", ":b")
        }

        and:
        messages1 == ["It works from project :a", "It works from project :b"]
        def streamedModels1 = listener.models as List<SomeToolingModel>
        streamedModels1.message == ["It works from project :a", "It works from project :b"]

        when: "only one project changes"
        buildFile "b/build.gradle", """
            myExtension.message = "It works from updated project :b"
        """

        and:
        listener = new TestStreamedValueListener()
        withIsolatedProjects()
        def messages2 = runBuildAction(new StreamCustomModelForEachProject()) {
            setStreamedValueListener(listener)
        }

        then: "only one model is recreated"
        fixture.assertModelUpdated {
            fileChanged("b/build.gradle")
            projectsConfigured(":buildSrc", ":", ":b")
            modelsCreated(":b")
            modelsReused(":buildSrc", ":", ":a")
        }

        and: "client receives updated models"
        messages2 == ["It works from project :a", "It works from updated project :b"]
        def streamedModels2 = listener.models as List<SomeToolingModel>
        streamedModels2.message == ["It works from project :a", "It works from updated project :b"]

        when: "running without changes after a partial update"
        listener = new TestStreamedValueListener()
        withIsolatedProjects()
        def messages3 = runBuildAction(new StreamCustomModelForEachProject()) {
            setStreamedValueListener(listener)
        }

        then:
        fixture.assertModelLoaded()

        and: "reused models are still correct after a partial update"
        messages3 == ["It works from project :a", "It works from updated project :b"]
        def streamedModels3 = listener.models as List<SomeToolingModel>
        streamedModels3.message == ["It works from project :a", "It works from updated project :b"]
    }

    private static class TestStreamedValueListener implements StreamedValueListener {
        def models = new CopyOnWriteArrayList<Object>()

        @Override
        void onValue(Object value) {
            models += value
        }
    }
}
