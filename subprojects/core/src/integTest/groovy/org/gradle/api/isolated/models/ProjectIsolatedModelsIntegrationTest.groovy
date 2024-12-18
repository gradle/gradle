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

package org.gradle.api.isolated.models


import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class ProjectIsolatedModelsIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        // Required, because the Gradle API jar is computed once a day,
        // and the new API might not be visible for tests that require compilation
        // against that API, e.g. the cases like a plugin defined in an included build
        executer.requireOwnGradleUserHomeDir()
    }

    def "project-scoped model state is isolated per consuming project"() {
        settingsFile """
            rootProject.name = "root"
            include("a", "b")
        """

        buildFile "buildSrc/build.gradle", """
            plugins { id 'groovy-gradle-plugin' }
        """

        buildFile "buildSrc/src/main/groovy/my-plugin.gradle", """
            def model1 = isolated.models.fromProjects("someKey", List<String>, [rootProject]).get(rootProject.isolated).get()
            model1 << project.name
            println("project '\$path' model[someKey][v1] = \$model1")

            def model2 = isolated.models.fromProjects("someKey", List<String>, [rootProject]).get(rootProject.isolated).get()
            model2 << project.name
            println("project '\$path' model[someKey][v2] = \$model2")

            assert model1 === model2
        """

        buildFile """
            def sharedList = ["root"]
            isolated.models.register("someKey", List<String>, providers.provider {
                println("Computing model for someKey")
                sharedList
            })
        """

        buildFile "a/build.gradle", """
            plugins { id 'my-plugin' }
        """
        buildFile "b/build.gradle", """
            plugins { id 'my-plugin' }
        """

        when:
        run "help"

        then:
        output.count("Computing model for someKey") == 1
        outputContains("project ':a' model[someKey][v1] = [root, a]")
        outputContains("project ':a' model[someKey][v2] = [root, a, a]")
        outputContains("project ':b' model[someKey][v1] = [root, b]")
        outputContains("project ':b' model[someKey][v2] = [root, b, b]")
    }

}
