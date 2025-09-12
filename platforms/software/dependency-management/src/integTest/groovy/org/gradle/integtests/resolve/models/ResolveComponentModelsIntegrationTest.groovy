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

package org.gradle.integtests.resolve.models

import org.gradle.api.provider.Property
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class ResolveComponentModelsIntegrationTest extends AbstractIntegrationSpec {

    def test() {
        file("buildSrc/src/main/java/example/SharedModel.java") << """
            package example;

            import ${Property.name};

            public interface SharedModel {

                Property<String> getName();

            }
        """

        settingsFile("""
            include("producer")
            include("transitive")
            include("direct")
        """)

        buildFile("producer/build.gradle", """
            plugins {
                id("java-library")
            }

            dependencies {
                implementation(project(":transitive"))
            }

            models.register(example.SharedModel) {
                name = "Producer Model"
            }
        """)

        buildFile("transitive/build.gradle", """
            plugins {
                id("java-library")
            }

            models.register(example.SharedModel) {
                name = "Transitive Model"
            }
        """)

        buildFile("direct/build.gradle", """
            plugins {
                id("java-library")
            }

            models.register(example.SharedModel) {
                name = "Direct Model"
            }
        """)

        buildFile("""
            import example.SharedModel

            plugins {
                id("java-library")
            }

            dependencies {
                implementation(project(":producer"))
                implementation(project(":direct"))
            }

            Provider<Collection<ComponentModel<SharedModel>>> models = configurations.runtimeClasspath.incoming
                .asComponentSet()
                .selectModel(SharedModel)

            models.get().each { model ->
                println(model.getComponentId().getDisplayName() + " - " + model.getModel().getName().get())
            }
        """)

        when:
        succeeds("help")

        then:
        outputContains("""project :producer - Producer Model
project :direct - Direct Model
project :transitive - Transitive Model""")
    }

    // TODO: Test trying to set property on consumer side (this should fail)
    // TODO: Test trying to load the model from a project that does not have the original model type on the classpath (we should do fuzzy re-hydrating of models)
    // TODO: Test getting a model provider directly from another project instance -- project(":producer").isolated.findModel(SharedModel)
    // TODO: Test getting a set of model providers from all direct subprojects -- project.getChildren().selectModel(SharedModel)
    // TODO: Test getting a set of model providers from all subprojects -- project.getAllChildren().selectModel(SharedModel)
    // TODO: Test using a model provider as a task input
    // TODO: Test sharing task outputs using registered models, ensure consumers properly track task dependencies
    // TODO: Test cycles in models. These should fail.
    // TODO: Test leniency -- what if the model doesn't exist?
    // TODO: Test partial leniency -- what if some models exist but others don't?
    // TODO: Test graph leniency -- what if the graph partially fails but we want to extract models from the graph that succeeded?

}
