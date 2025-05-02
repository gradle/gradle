/*
 * Copyright 2021 the original author or authors.
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

class IsolatedProjectsToolingApiCoupledProjectsIntegrationTest extends AbstractIsolatedProjectsToolingApiIntegrationTest {

    def "projects are treated as coupled when parent mutates child project"() {
        given:
        withSomeToolingModelBuilderPluginInBuildSrc()
        settingsFile << """
            include("a")
            include("b")
            include("c")
        """
        file("build.gradle") << """
            project(":a") {
                plugins.apply(my.MyPlugin)
            }
            project(":b") {
                plugins.apply(my.MyPlugin)
            }
        """
        file("a/build.gradle") << """
            myExtension.message = "project a"
        """
        file("b/build.gradle") << """
            myExtension.message = "project b"
        """
        file("c/build.gradle") << """
            plugins.apply(my.MyPlugin)
        """

        when:
        withIsolatedProjects(WARN_PROBLEMS_CLI_OPT)
        def model = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model.size() == 3
        model[0].message == "project a"
        model[1].message == "project b"
        model[2].message == "It works from project :c"

        and:
        fixture.assertModelStoredWithProblems {
            projectConfigured(":buildSrc")
            projectsConfigured(":")
            buildModelCreated()
            modelsCreated(":a", ":b", ":c")
            problem("Build file 'build.gradle': line 3: Project ':' cannot access 'Project.plugins' functionality on another project ':a'")
            problem("Build file 'build.gradle': line 6: Project ':' cannot access 'Project.plugins' functionality on another project ':b'")
        }

        when:
        withIsolatedProjects()
        def model2 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model2.size() == 3
        model2[0].message == "project a"
        model2[1].message == "project b"
        model2[2].message == "It works from project :c"

        and:
        fixture.assertModelLoaded()

        when:
        file("build.gradle") << """
            project(":a") {
                afterEvaluate {
                    myExtension.message = "new project a"
                }
            }
        """
        withIsolatedProjects(WARN_PROBLEMS_CLI_OPT)
        def model3 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model3.size() == 3
        model3[0].message == "new project a"
        model3[1].message == "project b"
        model3[2].message == "It works from project :c"

        and:
        fixture.assertModelUpdatedWithProblems {
            fileChanged("build.gradle")
            projectConfigured(":buildSrc")
            modelsCreated(":a", ":b")
            modelsQueriedAndNotPresent(":")
            modelsReused(":c", ":buildSrc")
            problem("Build file 'build.gradle': line 10: Project ':' cannot access 'Project.afterEvaluate' functionality on another project ':a'")
            problem("Build file 'build.gradle': line 11: Project ':' cannot access 'myExtension' extension on another project ':a'")
            problem("Build file 'build.gradle': line 3: Project ':' cannot access 'Project.plugins' functionality on another project ':a'")
            problem("Build file 'build.gradle': line 6: Project ':' cannot access 'Project.plugins' functionality on another project ':b'")
        }
    }

    def "projects are treated as coupled when child mutates parent project"() {
        given:
        withSomeToolingModelBuilderPluginInBuildSrc()
        settingsFile << """
            include("a")
            include("b")
        """
        file("a/build.gradle") << """
            parent.plugins.apply(my.MyPlugin)
            parent.myExtension.message = "root project"
        """
        file("b/build.gradle") << """
            plugins.apply(my.MyPlugin)
        """

        when:
        withIsolatedProjects(WARN_PROBLEMS_CLI_OPT)
        def model = runBuildAction(new FetchCustomModelForEachProject())

        then:
        // TODO - should include model from root
        model.size() == 1
        model[0].message == "It works from project :b"

        and:
        fixture.assertModelStoredWithProblems {
            projectConfigured(":buildSrc")
            projectsConfigured(":", ":a")
            buildModelCreated()
            // TODO - should create model for root
            modelsCreated(":b")
            problem("Build file 'a/build.gradle': line 2: Project ':a' cannot access 'Project.plugins' functionality on another project ':'")
            problem("Build file 'a/build.gradle': line 3: Project ':a' cannot access 'myExtension' extension on another project ':'")
        }

        when:
        withIsolatedProjects()
        def model2 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model2.size() == 1
        model2[0].message == "It works from project :b"

        and:
        fixture.assertModelLoaded()

        when:
        file("a/build.gradle") << """
            // Some change
        """
        withIsolatedProjects(WARN_PROBLEMS_CLI_OPT)
        def model3 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model3.size() == 1
        model3[0].message == "It works from project :b"

        and:
        fixture.assertModelUpdatedWithProblems {
            fileChanged("a/build.gradle")
            projectConfigured(":buildSrc")
            modelsQueriedAndNotPresent(":", ":a")
            modelsReused(":b", ":buildSrc")
            problem("Build file 'a/build.gradle': line 2: Project ':a' cannot access 'Project.plugins' functionality on another project ':'")
            problem("Build file 'a/build.gradle': line 3: Project ':a' cannot access 'myExtension' extension on another project ':'")
        }
    }

    def "projects are treated as coupled when project queries a sibling project"() {
        given:
        withSomeToolingModelBuilderPluginInBuildSrc()
        settingsFile << """
            include("a")
            include("b")
            include("c")
        """
        file("a/build.gradle") << """
            plugins.apply(my.MyPlugin)
            myExtension.message = "the message"
        """
        file("b/build.gradle") << """
            plugins.apply(my.MyPlugin)
            def otherProject = project(':a')
            myExtension.message = otherProject.myExtension.message
        """

        when:
        withIsolatedProjects(WARN_PROBLEMS_CLI_OPT)
        def model = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model.size() == 2
        model[0].message == "the message"
        model[1].message == "the message"

        and:
        fixture.assertModelStoredWithProblems {
            projectConfigured(":buildSrc")
            projectsConfigured(":", ":c")
            buildModelCreated()
            modelsCreated(":a", ":b")
            problem("Build file 'b/build.gradle': line 4: Project ':b' cannot access 'myExtension' extension on another project ':a'")
        }

        when:
        withIsolatedProjects()
        def model2 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model2.size() == 2
        model2[0].message == "the message"
        model2[1].message == "the message"

        and:
        fixture.assertModelLoaded()

        when:
        file("b/build.gradle") << """
            // some change
        """
        withIsolatedProjects(WARN_PROBLEMS_CLI_OPT)
        def model3 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model3.size() == 2
        model3[0].message == "the message"
        model3[1].message == "the message"

        and:
        fixture.assertModelUpdatedWithProblems {
            fileChanged("b/build.gradle")
            projectConfigured(":buildSrc")
            projectConfigured(":")
            modelsCreated(":a", ":b")
            modelsReused(":", ":c", ":buildSrc")
            problem("Build file 'b/build.gradle': line 4: Project ':b' cannot access 'myExtension' extension on another project ':a'")
        }

        when:
        withIsolatedProjects()
        def model4 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model4.size() == 2
        model4[0].message == "the message"
        model4[1].message == "the message"

        and:
        fixture.assertModelLoaded()

        file("a/build.gradle") << """
            myExtension.message = "new message"
        """
        withIsolatedProjects(WARN_PROBLEMS_CLI_OPT)
        def model5 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model5.size() == 2
        model5[0].message == "new message"
        model5[1].message == "new message"

        and:
        fixture.assertModelUpdatedWithProblems {
            fileChanged("a/build.gradle")
            projectConfigured(":buildSrc")
            projectConfigured(":")
            modelsCreated(":a", ":b")
            modelsReused(":", ":c", ":buildSrc")
            problem("Build file 'b/build.gradle': line 4: Project ':b' cannot access 'myExtension' extension on another project ':a'")
        }
    }

    def "can ignore project couplings using internal system property"() {
        given:
        withSomeToolingModelBuilderPluginInBuildSrc()
        settingsFile << """
            include("a")
            include("b")
            include("c")
        """
        file("a/build.gradle") << """
            plugins.apply(my.MyPlugin)
            myExtension.message = "the message"
        """
        file("b/build.gradle") << """
            plugins.apply(my.MyPlugin)
            def otherProject = project(':a')
            def otherProjectExtension = otherProject.myExtension
            myExtension.message = otherProjectExtension != null ? otherProjectExtension.message : 'default message'
        """

        when:
        withIsolatedProjects(WARN_PROBLEMS_CLI_OPT)
        def model = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model.size() == 2
        model[0].message == "the message"
        model[1].message == "the message"

        and:
        fixture.assertModelStoredWithProblems {
            projectConfigured(":buildSrc")
            projectsConfigured(":", ":c")
            buildModelCreated()
            modelsCreated(":a", ":b")
            problem("Build file 'b/build.gradle': line 4: Project ':b' cannot access 'myExtension' extension on another project ':a'")
        }

        when:
        withIsolatedProjects()
        def model2 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model2.size() == 2
        model2[0].message == "the message"
        model2[1].message == "the message"

        and:
        fixture.assertModelLoaded()

        when:
        file("b/build.gradle") << """
            // some change
        """
        withIsolatedProjects(WARN_PROBLEMS_CLI_OPT, "-Dorg.gradle.internal.invalidate-coupled-projects=false")
        def model3 = runBuildAction (new FetchCustomModelForEachProject())

        then:
        model3.size() == 2
        model3[0].message == "the message"
        model3[1].message == "default message"

        and:
        fixture.assertModelUpdatedWithProblems {
            fileChanged("b/build.gradle")
            projectConfigured(":buildSrc")
            projectsConfigured(":", ":b")
            modelsCreated(":b")
            modelsReused(":", ":a", ":c", ":buildSrc")
            problem("Build file 'b/build.gradle': line 4: Project ':b' cannot access 'myExtension' extension on another project ':a'. Setting 'org.gradle.internal.invalidate-coupled-projects=false' is preventing configuration of another project ':a'")
        }
    }
}
