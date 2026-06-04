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
        includeProjects("a", "b", "c")
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

        when: "the coupling violation fails the build"
        withIsolatedProjectsUsing(mode)
        runBuildActionFails(new FetchCustomModelForEachProject())

        then:
        if (mode == IsolatedProjectsMode.DIAGNOSTICS) {
            fixture.assertModelStoredAndDiscarded {
                projectConfigured(":buildSrc")
                projectsConfigured(":")
                buildModelCreated()
                modelsCreated(":a", ":b", ":c")
                problem("Build file 'build.gradle': line 3: Project ':' cannot access 'Project.plugins' functionality on another project ':a'")
                problem("Build file 'build.gradle': line 6: Project ':' cannot access 'Project.plugins' functionality on another project ':b'")
            }
        } else {
            failureCauseContains("Project ':' cannot access 'Project.plugins' functionality on another project ':a'")
        }

        where:
        mode << ALL_MODES
    }

    def "projects are treated as coupled when child mutates parent project"() {
        given:
        withSomeToolingModelBuilderPluginInBuildSrc()
        includeProjects("a", "b")
        file("a/build.gradle") << """
            parent.plugins.apply(my.MyPlugin)
            parent.myExtension.message = "root project"
        """
        file("b/build.gradle") << """
            plugins.apply(my.MyPlugin)
        """

        when: "the coupling violation fails the build"
        withIsolatedProjectsUsing(mode)
        runBuildActionFails(new FetchCustomModelForEachProject())

        then:
        if (mode == IsolatedProjectsMode.DIAGNOSTICS) {
            fixture.assertModelStoredAndDiscarded {
                projectConfigured(":buildSrc")
                projectsConfigured(":", ":a")
                buildModelCreated()
                modelsCreated(":", ":b")
                problem("Build file 'a/build.gradle': line 2: Project ':a' cannot access 'Project.plugins' functionality on another project ':'")
                problem("Build file 'a/build.gradle': line 3: Project ':a' cannot access 'myExtension' extension on another project ':'")
            }
        } else {
            failureCauseContains("Project ':a' cannot access 'Project.plugins' functionality on another project ':'")
        }

        where:
        mode << ALL_MODES
    }

    def "projects are treated as coupled when project queries a sibling project"() {
        given:
        withSomeToolingModelBuilderPluginInBuildSrc()
        includeProjects("a", "b", "c")
        file("a/build.gradle") << """
            plugins.apply(my.MyPlugin)
            myExtension.message = "the message"
        """
        file("b/build.gradle") << """
            plugins.apply(my.MyPlugin)
            def otherProject = project(':a')
            myExtension.message = otherProject.myExtension.message
        """

        when: "the coupling violation fails the build"
        withIsolatedProjectsUsing(mode)
        runBuildActionFails(new FetchCustomModelForEachProject())

        then:
        if (mode == IsolatedProjectsMode.DIAGNOSTICS) {
            fixture.assertModelStoredAndDiscarded {
                projectConfigured(":buildSrc")
                projectsConfigured(":", ":c")
                buildModelCreated()
                modelsCreated(":a", ":b")
                problem("Build file 'b/build.gradle': line 4: Project ':b' cannot access 'myExtension' extension on another project ':a'")
            }
        } else {
            failureCauseContains("Project ':b' cannot access 'myExtension' extension on another project ':a'")
        }

        where:
        mode << ALL_MODES
    }

    def "coupling violation fails the build fast even with invalidate-coupled-projects disabled"() {
        // The internal `invalidate-coupled-projects=false` escape hatch only governs reuse-run invalidation
        // of coupled projects; it does not suppress the IP violation, which fails fast in default mode.
        // The prior coverage of the property's reuse-run behaviour required a reusable entry stored despite
        // the violation (under the old warn-mode deferral), which no longer exists.
        given:
        withSomeToolingModelBuilderPluginInBuildSrc()
        includeProjects("a", "b", "c")
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
        withIsolatedProjects("-Dorg.gradle.internal.invalidate-coupled-projects=false")
        runBuildActionFails(new FetchCustomModelForEachProject())

        then:
        failureCauseContains("Project ':b' cannot access 'myExtension' extension on another project ':a'")
    }
}
