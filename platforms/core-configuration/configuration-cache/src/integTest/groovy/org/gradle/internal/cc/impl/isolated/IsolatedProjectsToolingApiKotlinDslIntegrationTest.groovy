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

package org.gradle.internal.cc.impl.isolated

import org.gradle.integtests.fixtures.build.KotlinDslTestProjectInitiation
import org.gradle.tooling.BuildAction
import org.gradle.tooling.BuildController
import org.gradle.tooling.FetchModelResult
import org.gradle.tooling.model.gradle.GradleBuild
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel

import static org.gradle.kotlin.dsl.tooling.fixtures.KotlinDslModelChecker.checkKotlinDslScriptsModel

class IsolatedProjectsToolingApiKotlinDslIntegrationTest extends AbstractIsolatedProjectsToolingApiIntegrationTest implements KotlinDslTestProjectInitiation {

    def isolatedScriptsModel = "org.gradle.kotlin.dsl.tooling.builders.internal.IsolatedScriptsModel"

    def "can fetch KotlinDslScripts model for single subproject build"() {
        withSettings("""
            rootProject.name = "root"
            include("a")
        """)
        withBuildScript()
        withBuildScriptIn("a")

        when:
        def originalModel = fetchModel(KotlinDslScriptsModel)

        then:
        fixture.assertNoConfigurationCache()

        when:
        withIsolatedProjects()
        def model = fetchModel(KotlinDslScriptsModel)

        then:
        fixture.assertModelStored {
            modelsCreated(":", KotlinDslScriptsModel)
            modelsCreated(":a", [isolatedScriptsModel])
        }
        checkKotlinDslScriptsModel(model, originalModel)

        when:
        withIsolatedProjects()
        fetchModel(KotlinDslScriptsModel)

        then:
        fixture.assertModelLoaded()
    }

    def "resilient fetch of KotlinDslScripts model for multi-project build under IP does not deadlock"() {
        // Reproduces the stage-1 accessor deadlock from PR #37967: the resilient, project-targeted
        // BuildController.fetch holds the root project lock while the model builder fans out workers
        // that contend for the shared stage-1 accessor classpath lazy. The root has no build script
        // so the lazy is first touched concurrently by the fan-out workers, not the outer thread.
        withSettings("""
            rootProject.name = "root"
            include("a", "b", "c", "d")
        """)
        withBuildScriptIn("a")
        withBuildScriptIn("b")
        withBuildScriptIn("c")
        withBuildScriptIn("d")

        when:
        def originalModel = fetchModel(KotlinDslScriptsModel)

        then:
        fixture.assertNoConfigurationCache()

        when:
        withIsolatedProjects()
        def model = runBuildAction(new FetchResilientKotlinDslScriptsModelForRoot())

        then:
        checkKotlinDslScriptsModel(model, originalModel)
    }

    def "can fetch KotlinDslScripts model for build with third party buildscript dependency"() {
        withSettings("""
            rootProject.name = "root"
            include("a")
            include("a:b")
        """)
        withBuildScript()

        // We exercise source path of the hierarchy.
        // :a declares a buildscript dependency which sources must be visible in :a:b
        withBuildScriptIn("a", """
            buildscript {
                $repositoriesBlock
                dependencies { classpath("commons-io:commons-io:2.18.0") }
            }
        """)
        withBuildScriptIn("a/b")

        when:
        def originalModel = fetchModel(KotlinDslScriptsModel)

        then:
        fixture.assertNoConfigurationCache()

        when:
        withIsolatedProjects()
        def model = fetchModel(KotlinDslScriptsModel)

        then:
        fixture.assertModelStored {
            modelsCreated(":", KotlinDslScriptsModel)
            modelsCreated(":a", [isolatedScriptsModel])
            modelsCreated(":a:b", [isolatedScriptsModel])
        }
        checkKotlinDslScriptsModel(model, originalModel)
    }

    static class FetchResilientKotlinDslScriptsModelForRoot implements BuildAction<KotlinDslScriptsModel>, Serializable {

        @Override
        KotlinDslScriptsModel execute(BuildController controller) {
            GradleBuild build = controller.getBuildModel()
            FetchModelResult<KotlinDslScriptsModel> result = controller.fetch(build.rootProject, KotlinDslScriptsModel)
            return result.model
        }
    }
}
