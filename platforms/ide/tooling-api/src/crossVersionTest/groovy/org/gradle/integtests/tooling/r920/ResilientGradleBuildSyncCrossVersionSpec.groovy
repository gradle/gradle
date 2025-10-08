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

package org.gradle.integtests.tooling.r920

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.test.fixtures.file.TestFile
import org.gradle.tooling.BuildAction
import org.gradle.tooling.BuildController
import org.gradle.tooling.FetchModelResult
import org.gradle.tooling.model.Model
import org.gradle.tooling.model.ProjectIdentifier
import org.gradle.tooling.model.gradle.ResilientGradleBuild
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptModel
import spock.lang.Unroll

@ToolingApiVersion('>=9.3')
@TargetGradleVersion('>=9.3')
class ResilientGradleBuildSyncCrossVersionSpec extends ToolingApiSpecification {
    static final String RESILIENT_MODEL_TRUE = "-Dorg.gradle.internal.resilient-model-building=true"

    def setup() {
        settingsFile.delete() // This is automatically created by `ToolingApiSpecification`
    }

    def runCustomModelAction() {
        succeeds {
            action(new CustomModelAction())
                .withArguments(RESILIENT_MODEL_TRUE)
                .run()
        }
    }

    def runFetchModelAction() {
        succeeds {
            action(new FetchModelAction())
                .withArguments(RESILIENT_MODEL_TRUE)
                .run()
        }
    }

    def runModelAction(String actionType) {
        actionType == 'custom' ? runCustomModelAction() : runFetchModelAction()
    }

    @Unroll
    def "receive root project with broken settings file [#actionType]"() {
        given:
        settingsKotlinFile << """
            blow up !!!
        """
        withStackTraceChecksDisabled()

        when:
        def model = runModelAction(actionType)

        then:
        if (actionType == 'custom') {
            model.build.didItFail()
            model.build.failures != null
            model.build.failures.toString().contains("Script compilation error")
        } else {
            model.model.didItFail()
            model.failures != null
            model.failures.toString().contains("Script compilation error")
        }

        where:
        actionType << ['custom', 'fetch']
    }

    @Unroll
    def "receive root project with broken root build file [#actionType]"() {
        given:
        settingsKotlinFile << """
            rootProject.name = "root"
        """
        blowUpBuildGradleKts()

        when:
        def model = runModelAction(actionType)

        then:
        if (actionType == 'custom') {
            model.paths == [":"]
            !model.build.didItFail()
        } else {
            !model.model.didItFail()
        }

        where:
        actionType << ['custom', 'fetch']
    }

    @Unroll
    def "receive root project and included build root project with broken included build file [#actionType]"() {
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
        def model = runModelAction(actionType)

        then:
        if (actionType == 'custom') {
            model.paths == [":", ":included"]
            model.build.gradleBuild != null
            def includedBuild = model.build.gradleBuild.includedBuilds.getAt(0)
            includedBuild != null
        } else {
            model.model.gradleBuild != null
            model.model.gradleBuild.includedBuilds.getAt(0) != null
        }

        where:
        actionType << ['custom', 'fetch']
    }

    @Unroll
    def "receive root project and included plugin project root with broken included build file [#actionType]"() {
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
        def model = runModelAction(actionType)

        then:
        if (actionType == 'custom') {
            model.paths == [":", ":included-plugin"]
            !model.build.didItFail()
        } else {
            !model.model.didItFail()
            model.model.gradleBuild.includedBuilds.getAt(0) != null
        }

        where:
        actionType << ['custom', 'fetch']
    }

    @Unroll
    def "receive root project and included build root project (non-relative) with broken included settings file [#actionType]"() {
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
        withStackTraceChecksDisabled()

        when:
        def model = runModelAction(actionType)

        then:
        if (actionType == 'custom') {
            model.paths == [":", ":"]
            model.build.didItFail()
            model.build != null
            model.build.failures != null
            model.build.failures.toString().contains("Script compilation error")
            def includedBuild = model.build.gradleBuild.includedBuilds.getAt(0)
            includedBuild != null
        } else {
            model.model.didItFail()
            model.model.gradleBuild != null
            model.failures != null
            model.failures.toString().contains("Script compilation error")
            model.model.gradleBuild.includedBuilds.getAt(0) != null
        }

        where:
        actionType << ['custom', 'fetch']
    }

    TestFile blowUpBuildGradleKts(TestFile included = null) {
        def blowUpString = """
                blow up !!!
            """
        if(included == null) {
            buildFileKts << blowUpString
        }else{
            included.file("build.gradle.kts") << blowUpString
        }
    }

    static class MyCustomModel implements Serializable {
        List<ProjectIdentifier> projectIdentifiers;
        List<String> paths;
        Map<File, KotlinDslScriptModel> scriptModels;
        ResilientGradleBuild build

        MyCustomModel(Map<File, KotlinDslScriptModel> models,
                      List<ProjectIdentifier> projectIdentifiers,
                      List<String> paths,
                      ResilientGradleBuild build) {
            this.build = build
            this.projectIdentifiers = projectIdentifiers;
            this.paths = paths;
            this.scriptModels = models;
        }
    }

    static class FetchModelAction implements BuildAction<FetchModelResult<Model, ResilientGradleBuild>>, Serializable {
        @Override
        FetchModelResult<Model, ResilientGradleBuild> execute(BuildController controller) {
            return controller.fetch(null, ResilientGradleBuild, null, null)
        }
    }

    static class CustomModelAction implements BuildAction<MyCustomModel>, Serializable {
        @Override
        MyCustomModel execute(BuildController controller) {
            ResilientGradleBuild build = controller.getModel(ResilientGradleBuild.class);

            if (build.didItFail()) {
                System.err.println("Build failed: " + build.failures);
            }

            def includedBuilds = build.gradleBuild.includedBuilds

            def paths = build.gradleBuild.projects.collect { project ->
                project.buildTreePath
            }
            includedBuilds.each { gb ->
                gb.projects.each { project ->
                    paths << project.buildTreePath
                }
            }

            def identifier = build.gradleBuild.projects.collect { project ->
                project.projectIdentifier
            }

            // Build your custom model
            return new MyCustomModel(
                [:],
                identifier,
                paths,
                build
            );
        }
    }
}
