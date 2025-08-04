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

package org.gradle.integtests.tooling.r92

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.BuildAction
import org.gradle.tooling.BuildController
import org.gradle.tooling.model.ProjectIdentifier
import org.gradle.tooling.model.gradle.GradleBuild
import org.gradle.tooling.model.kotlin.dsl.KotlinDslModelsParameters
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptModel

@ToolingApiVersion('>=9.2')
// Since our action uses `buildTreePath`
@TargetGradleVersion('>=9.2')
class FailedSyncCrossVersionSpec extends ToolingApiSpecification {

    def setup() {
        settingsFile.delete() // This is automatically created by `ToolingApiSpecification`
    }

    //    def "broken settings file - strict mode- build action"() {
//        given:
//        settingsKotlinFile << """
//            blow up !!!
//        """
//
//        when:
//        executeResilient()
//        MyCustomModel model = runBuildAction(new CustomModelAction())
//
//        then:
//        model.paths == [":"]
//        model.build != null
//        model.build.didItFail()
//        model.build.failure != null
//        model.build.failure.message.contains("Script compilation error")
//    }
//

    def "broken settings file - strict mode - build action"() {
        given:
        settingsKotlinFile << """
            blow up !!!
        """

        when:
        def model = succeeds {
            action(new CustomModelAction())
                .withArguments("-Dorg.gradle.internal.resilient-model-building=true")
                .run()
        }

        then:
        model.paths == [":"]
        model.build != null
        model.build.didItFail()
        model.build.failure != null
        model.build.failure.message.contains("Script compilation error")

//        def e = thrown(BuildActionFailureException)
//        e.cause.message.contains(settingsKotlinFile.absolutePath)
//
//        failure.assertHasDescription("Script compilation error")
    }

    def "basic project - broken root build file with build action"() {
        given:
        settingsKotlinFile << """
            rootProject.name = "root"
        """
        buildKotlinFile << """
            blow up !!!
        """

        when:
        MyCustomModel model = succeeds {
            action(new CustomModelAction())
                .withArguments(KotlinDslModelsParameters.CLASSPATH_MODE_SYSTEM_PROPERTY_DECLARATION)
                .run()
        }

        then:
        model.paths == [":"]
    }


//    def "basic project w/ included build - broken included build build file - build action"() {
//        given:
//        settingsKotlinFile << """
//            rootProject.name = "root"
//            includeBuild("included")
//        """
//
//        def included = testDirectory.createDir("included")
//        included.file("settings.gradle.kts") << """
//            rootProject.name = "included"
//        """
//        included.file("build.gradle.kts") << """
//            blow up !!!
//        """
//
//        when:
//        executeResilient()
//        MyCustomModel model = runBuildAction(new CustomModelAction())
//
//        then:
//        model.paths == [":", ":included"]
//        model.build != null
//
//        def inlcudedBuild = model.build.includedBuilds.getAt(0)
//        inlcudedBuild != null
//        inlcudedBuild.failure == null
//    }
//
    def "basic project w/ included build - broken included build build file - build action"() {
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
        MyCustomModel model = succeeds {
            action(new CustomModelAction())
                .withArguments("-Dorg.gradle.internal.resilient-model-building=true")
                .run()
        }

        then:
        model.paths == [":", ":included"]
        model.build != null

        def inlcudedBuild = model.build.includedBuilds.getAt(0)
        inlcudedBuild != null
        inlcudedBuild.failure == null
    }


    def "basic project w/ included build in pluginManagement - broken included build build file - build action"() {
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
        MyCustomModel model = succeeds {
            action(new CustomModelAction())
                .withArguments("-Dorg.gradle.internal.resilient-model-building=true")
                .run()
        }
        then:
        model.paths == [":", ":included-plugin"]
    }

    def "basic project w/ included build - broken included build settings file and build script - strict mode - build action"() {
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
        def model = succeeds {
            action(new CustomModelAction())
                .withArguments("-Dorg.gradle.internal.resilient-model-building=true")
                .run()
        }

        then:
        model.paths == [":", ":"]
        model.build != null

        def includedBuild = model.build.includedBuilds.getAt(0)
        includedBuild != null
        includedBuild.failure != null
        includedBuild.failure.message.contains("Script compilation error")
    }

//    static class MyCustomModel implements Serializable {
//
//        Map<File, KotlinDslScriptModel> scriptModels
//        List<ProjectIdentifier> projectIdentifiers
//        List<String> paths
//
//        MyCustomModel(
//            Map<File, KotlinDslScriptModel> scriptModels,
//            List<ProjectIdentifier> projectIdentifiers,
//            List<String> paths
//        ) {
//            this.scriptModels = scriptModels
//            this.projectIdentifiers = projectIdentifiers
//            this.paths = paths
//        }
//
//    }

    static class MyCustomModel implements Serializable {
        List<ProjectIdentifier> projectIdentifiers;
        List<String> paths;
        Map<File, KotlinDslScriptModel> scriptModels;
        GradleBuild build

        MyCustomModel(Map<File, KotlinDslScriptModel> models,
                      List<ProjectIdentifier> projectIdentifiers,
                      List<String> paths,
                      GradleBuild build) {
            this.build = build
            this.projectIdentifiers = projectIdentifiers;
            this.paths = paths;
            this.scriptModels = models;
        }
    }

//    static class CustomModelAction implements BuildAction<MyCustomModel>, Serializable {
//
//        @Override
//        MyCustomModel execute(BuildController controller) {
//            GradleBuild build = controller.getModel(GradleBuild.class)
//            KotlinDslScriptsModel buildScriptModel = controller.getModel(KotlinDslScriptsModel.class)
//
//            def paths = Stream.concat(Stream.of(build), build.includedBuilds.stream())
//                .flatMap(b -> b.projects.stream())
//                .map(p -> p.buildTreePath)
//                .collect(Collectors.toList())
//
//            def identifier = build.projects.collect { project ->
//                project.projectIdentifier
//            }
//
//            // Build your custom model
//            return new MyCustomModel(
//                buildScriptModel.scriptModels,
//                identifier,
//                paths
//            )
//        }
//
//    }

    static class CustomModelAction implements BuildAction<MyCustomModel>, Serializable {

        @Override
        public MyCustomModel execute(BuildController controller) {
            GradleBuild build
            try {

                build = controller.getModel(GradleBuild.class);
            }
            catch (Exception e) {
                System.err.println(e.toString());
            }

            if (build.didItFail()) {
                System.err.println("Build failed: " + build.failure);
            }


            if (build.includedBuilds.size() > 0) {
                GradleBuild b = build.includedBuilds.getAt(0)
                if (b.didItFail()) {
                    System.err.println("Build failed: " + b.failure.description);
                }
            }
//        KotlinDslScriptsModel buildScriptModel = controller.getModel(KotlinDslScriptsModel.class);


            def paths = build.projects.collect { project ->
                project.buildTreePath
            }
            build.includedBuilds.each { gb ->
                gb.projects.each { project ->
                    paths << project.buildTreePath
                }
            }

            def identifier = build.projects.collect { project ->
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

//package org.gradle.integtests.tooling
//
//import org.gradle.tooling.BuildAction
//import org.gradle.tooling.BuildController
//import org.gradle.tooling.model.gradle.GradleBuild
//
//class CustomModelAction implements BuildAction<MyCustomModel>, Serializable {
//
//    @Override
//    public MyCustomModel execute(BuildController controller) {
//        GradleBuild build
//        try {
//
//            build = controller.getModel(GradleBuild.class);
//        }
//        catch (Exception e) {
//            System.err.println(e.toString());
//        }
//
//        if(build.didItFail()){
//            System.err.println("Build failed: " + build.failure);
//        }
//
//
//        if(build.includedBuilds.size() > 0) {
//            GradleBuild b = build.includedBuilds.getAt(0)
//            if(b.didItFail()){
//                System.err.println("Build failed: " + b.failure.description);
//            }
//        }
////        KotlinDslScriptsModel buildScriptModel = controller.getModel(KotlinDslScriptsModel.class);
//
//
//        def paths = build.projects.collect{project ->
//            project.buildTreePath
//        }
//        build.includedBuilds.each {gb -> gb.projects.each {project ->
//            paths << project.buildTreePath
//        }}
//
//        def identifier = build.projects.collect{project ->
//            project.projectIdentifier
//        }
//
//        // Build your custom model
//        return new MyCustomModel(
//            [:],
//            identifier,
//            paths,
//            build
//        );
//    }
//}


//package org.gradle.integtests.tooling
//
//import org.gradle.tooling.model.ProjectIdentifier
//import org.gradle.tooling.model.gradle.GradleBuild
//import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptModel;
//
//class MyCustomModel implements Serializable {
//    List<ProjectIdentifier> projectIdentifiers;
//    List<String> paths;
//    Map<File, KotlinDslScriptModel> scriptModels;
//    GradleBuild build
//
//    MyCustomModel(Map<File, KotlinDslScriptModel> models,
//                  List<ProjectIdentifier> projectIdentifiers,
//                  List<String> paths,
//                  GradleBuild build) {
//        this.build = build
//        this.projectIdentifiers = projectIdentifiers;
//        this.paths = paths;
//        this.scriptModels = models;
//    }
//}

//package org.gradle.integtests.tooling
//
//import org.gradle.integtests.fixtures.AbstractIntegrationSpec
//import org.gradle.integtests.fixtures.executer.GradleExecuter
//import org.gradle.integtests.tooling.fixture.ToolingApiBackedGradleExecuter
//import org.gradle.integtests.tooling.fixture.ToolingApiSpec
//import org.gradle.tooling.model.kotlin.dsl.KotlinDslModelsParameters
//
//class FailedSyncIntegrationTest extends AbstractIntegrationSpec implements ToolingApiSpec {
//

//    def "basic project - broken root build file with build action"() {
//        given:
//        settingsKotlinFile << """
//            rootProject.name = "root"
//        """
//        buildKotlinFile << """
//            blow up !!!
//        """
//
//        when:
//        executeResilient()
//        executer.withArguments(KotlinDslModelsParameters.CLASSPATH_MODE_SYSTEM_PROPERTY_DECLARATION)
//        MyCustomModel model = runBuildAction(new CustomModelAction())
//
//        then:
//        model.paths == [":"]
//        model.build != null
//    }
//
//    def "basic project w/ included build - broken included build build file - build action"() {
//        given:
//        settingsKotlinFile << """
//            rootProject.name = "root"
//            includeBuild("included")
//        """
//
//        def included = testDirectory.createDir("included")
//        included.file("settings.gradle.kts") << """
//            rootProject.name = "included"
//        """
//        included.file("build.gradle.kts") << """
//            blow up !!!
//        """
//
//        when:
//        executeResilient()
//        MyCustomModel model = runBuildAction(new CustomModelAction())
//
//        then:
//        model.paths == [":", ":included"]
//        model.build != null
//
//        def inlcudedBuild = model.build.includedBuilds.getAt(0)
//        inlcudedBuild != null
//        inlcudedBuild.failure == null
//    }
//
//    def GradleExecuter executeResilient() {
//        executer.withArguments("-Dorg.gradle.internal.resilient-model-building=true")
//    }
//
//    def "basic project w/ included build in pluginManagement - broken included build build file - build action"() {
//        given:
//        settingsKotlinFile << """
//        pluginManagement {
//            includeBuild("included-plugin")
//        }
//        rootProject.name = "root"
//    """
//
//        def includedPlugin = testDirectory.createDir("included-plugin")
//        includedPlugin.file("settings.gradle.kts") << """
//        rootProject.name = "included-plugin"
//    """
//        includedPlugin.file("build.gradle.kts") << """
//        blow up !!!
//    """
//
//        when:
//        executeResilient()
//        MyCustomModel model = runBuildAction(new CustomModelAction())
//
//        then:
//        model.paths == [":", ":included-plugin"]
//    }
//
//    def "basic project w/ included build in pluginManagement - broken included build settings file - strict mode - build action"() {
//        given:
//        settingsKotlinFile << """
//        pluginManagement {
//            includeBuild("included-plugin")
//        }
//        rootProject.name = "root"
//    """
//
//        def includedPlugin = testDirectory.createDir("included-plugin")
//        includedPlugin.file("settings.gradle.kts") << """
//        boom !!!
//    """
//        includedPlugin.file("build.gradle.kts") << """
//        plugins {
//            `kotlin-dsl`
//        }
//    """
//
//        when:
//        executeResilient()
//        MyCustomModel model = runBuildAction(new CustomModelAction())
//
//        then:
//        model.paths == [":"]
//        model.build != null
//
//        def inlcudedBuild = model.build.includedBuilds.getAt(0)
//        inlcudedBuild != null
//        inlcudedBuild.failure != null
//        inlcudedBuild.failure.message.contains("Script compilation error")
//    }
//
//    def "basic project w/ included build - broken included build settings file and build script - strict mode - build action"() {
//        given:
//        settingsKotlinFile << """
//            rootProject.name = "root"
//            includeBuild("included")
//        """
//
//        def included = testDirectory.createDir("included")
//        included.file("settings.gradle.kts") << """
//            boom !!!
//        """
//        included.file("build.gradle.kts") << """
//            blow up !!!
//        """
//
//        when:
//        executeResilient()
//        MyCustomModel model = runBuildAction(new CustomModelAction())
//
//        then:
//        model.paths == [":"]
//        model.build != null
//
//        def inlcudedBuild = model.build.includedBuilds.getAt(0)
//        inlcudedBuild != null
//        inlcudedBuild.failure != null
//        inlcudedBuild.failure.message.contains("Script compilation error")
//    }
//
//
//    @Override
//    GradleExecuter createExecuter() {
//        return new ToolingApiBackedGradleExecuter(distribution, temporaryFolder)
//    }
//
//}
