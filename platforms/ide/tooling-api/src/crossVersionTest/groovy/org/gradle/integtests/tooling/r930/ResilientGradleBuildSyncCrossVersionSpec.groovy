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

package org.gradle.integtests.tooling.r930

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.test.fixtures.file.TestFile
import org.gradle.tooling.BuildAction
import org.gradle.tooling.BuildActionExecuter
import org.gradle.tooling.BuildController
import org.gradle.tooling.model.gradle.GradleBuild

import java.util.function.Consumer

@ToolingApiVersion('>=9.3')
@TargetGradleVersion('>=9.3')
class ResilientGradleBuildSyncCrossVersionSpec extends ToolingApiSpecification {
    static final String RESILIENT_MODEL_TRUE = "-Dorg.gradle.internal.resilient-model-building=true"

    def setup() {
        settingsFile.delete() // This is automatically created by `ToolingApiSpecification`
    }

    BuildActionResult runFetchModelAction(Consumer<BuildActionExecuter<BuildActionResult>> configurer = {}) {
        succeeds {
            def action = action(new FetchModelAction())
                .withArguments(RESILIENT_MODEL_TRUE)
            configurer.accept(action)
            action.run()
        }
    }

    def "receive root project with broken settings file"() {
        given:
        settingsKotlinFile << """
            blow up !!!
        """

        when:
        def model = runFetchModelAction()

        then:
        model.failures.toString().contains("Script compilation error")
    }

    def "receive root project with throwing settings file"() {
        given:
        settingsKotlinFile << """
            throw GradleException("Gradle exception boom !!!")
        """

        when:
        def model = runFetchModelAction()

        then:
        model.failures.toString().contains("Gradle exception boom !!!")
    }

    def "receive root project with broken root build file"() {
        given:
        settingsKotlinFile << """
            rootProject.name = "root"
        """
        blowUpBuildGradleKts()

        when:
        def model = runFetchModelAction()

        then:
        model.failures.isEmpty()  // it did not fail
        model.model.includedBuilds.isEmpty()
    }

    def "receive root project with throwing root build file"() {
        given:
        settingsKotlinFile << """
            rootProject.name = "root"
        """
        buildFileKts << """
            throw GradleException("Gradle exception boom !!!")
        """

        when:
        def model = runFetchModelAction()

        then:
        model.failures.isEmpty()  // it did not fail
        model.model.includedBuilds.isEmpty()
    }

    def "receive root project and included build root project with broken included build file"() {
        given:
        settingsKotlinFile << """
            rootProject.name = "root"
            includeBuild("included")
        """

        def included = file("included")
        included.file("settings.gradle.kts") << """
            rootProject.name = "included"
        """
        blowUpBuildGradleKts(included)

        when:
        def model = runFetchModelAction()

        then:
        model.failures.isEmpty()
        !model.model.includedBuilds.isEmpty()
    }

    def "receive root project and included plugin project root with broken included build file"() {
        given:
        settingsKotlinFile << """
        pluginManagement {
            includeBuild("included-plugin")
        }
        rootProject.name = "root"
    """

        def includedPlugin = createDirs("included-plugin").get(0)
        includedPlugin.file("settings.gradle.kts") << """
        rootProject.name = "included-plugin"
    """
        blowUpBuildGradleKts(includedPlugin)

        when:
        def model = runFetchModelAction()

        then:
        model.failures.isEmpty()
        !model.model.includedBuilds.isEmpty()
    }

    def "receive root project and included build root project (non-relative) with broken included settings file"() {
        given:
        settingsKotlinFile << """
            rootProject.name = "root"
            includeBuild("included")
        """

        def included = file("included")
        included.file("settings.gradle.kts") << """
            settings boom !!!
        """
        blowUpBuildGradleKts(included)

        when:
        def model = runFetchModelAction()

        then:
        model.failures.toString().contains("Script compilation error")
        !model.model.includedBuilds.isEmpty()
    }

    def "should return failure when caching models with isolated projects"() {
        given:
        def intermediateCaching = [
            "-Dorg.gradle.internal.isolated-projects.tooling=true",
            "-Dorg.gradle.unsafe.isolated-projects=true"
        ]
        settingsKotlinFile << """
            rootProject.name = "root"
            includeBuild("included")
        """

        def included = file("included")
        included.file("settings.gradle.kts") << """
            settings boom !!!
        """
        blowUpBuildGradleKts(included)

        when:
        def model = runFetchModelAction(){
            it.addArguments(intermediateCaching)
        }

        then:
        model.failures.toString().contains("Script compilation error")
        !model.model.includedBuilds.isEmpty()

        when:
        model = runFetchModelAction {
            it.addArguments(intermediateCaching)
        }

        then:
        model.failures.toString().contains("Script compilation error")
        !model.model.includedBuilds.isEmpty()
    }

    TestFile blowUpBuildGradleKts(TestFile included = null) {
        def blowUpString = """
                blow up !!!
            """
        if (included == null) {
            buildFileKts << blowUpString
        } else {
            included.file("build.gradle.kts") << blowUpString
        }
    }

    static class BuildActionResult implements Serializable {
        final GradleBuild model
        final List<String> failures

        BuildActionResult(GradleBuild model, List<String> failures) {
            this.model = model
            this.failures = failures
        }
    }

    static class FetchModelAction implements BuildAction<BuildActionResult>, Serializable {
        @Override
        BuildActionResult execute(BuildController controller) {
            def result = controller.fetch(GradleBuild, null, null)
            return new BuildActionResult(result.getModel(), result.getFailures().collect { it.message })
        }
    }
}
