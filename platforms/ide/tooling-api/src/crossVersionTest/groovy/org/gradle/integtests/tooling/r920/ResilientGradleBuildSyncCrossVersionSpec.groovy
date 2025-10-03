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
import org.gradle.tooling.BuildAction
import org.gradle.tooling.BuildController
import org.gradle.tooling.model.ProjectIdentifier
import org.gradle.tooling.model.gradle.ResilientGradleBuild
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptModel

@ToolingApiVersion('>=9.3')
@TargetGradleVersion('>=9.3')
class ResilientGradleBuildSyncCrossVersionSpec extends ToolingApiSpecification {

    def setup() {
        settingsFile.delete() // This is automatically created by `ToolingApiSpecification`
    }

    def runCustomModelAction() {
        succeeds {
            action(new CustomModelAction())
                .withArguments("-Dorg.gradle.internal.resilient-model-building=true")
                .run()
        }
    }

    def "receive root project with broken settings file"() {
        given:
        settingsKotlinFile << """
            blow up !!!
        """

        when:
        def model = runCustomModelAction()

        then:
        model.paths == [":"]
        model.build != null
        model.build.didItFail()
        model.build.failures != null
        model.build.failures.toString().contains("Script compilation error")
    }

    def "receive root project with broken root build file"() {
        given:
        settingsKotlinFile << """
            rootProject.name = "root"
        """
        buildKotlinFile << """
            blow up !!!
        """

        when:
        MyCustomModel model = runCustomModelAction()

        then:
        model.paths == [":"]
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
        included.file("build.gradle.kts") << """
            blow up !!!
        """

        when:
        MyCustomModel model = runCustomModelAction()

        then:
        model.paths == [":", ":included"]
        model.build.gradleBuild != null

        def includedBuild = model.build.gradleBuild.includedBuilds.getAt(0)
        includedBuild != null
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
        includedPlugin.file("build.gradle.kts") << """
        blow up !!!
    """

        when:
        MyCustomModel model = runCustomModelAction()

        then:
        model.paths == [":", ":included-plugin"]
    }

    def "receive root project and included build root project (non-relative) with broken included settings file"() {
        given:
        settingsKotlinFile << """
            rootProject.name = "root"
            includeBuild("included")
        """

        def included = file("included")
        included.file("settings.gradle.kts") << """
            boom !!!
        """
        included.file("build.gradle.kts") << """
            blow up !!!
        """

        when:
        withStackTraceChecksDisabled()
        def model = runCustomModelAction()

        then:
        model.paths == [":", ":"]
        model.build != null
        model.build.failures != null
        model.build.failures.toString().contains("Script compilation error")

        def includedBuild = model.build.gradleBuild.includedBuilds.getAt(0)
        includedBuild != null
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

    static class CustomModelAction implements BuildAction<MyCustomModel>, Serializable {

        @Override
        public MyCustomModel execute(BuildController controller) {
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
