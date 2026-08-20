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

package org.gradle.internal.cc.impl.isolated

class IsolatedProjectsModelStateCorruptionRecoveryIntegrationTest extends AbstractIsolatedProjectsToolingApiIntegrationTest {

    def "discards a corrupted reused project state entry so the next build recovers"() {
        given:
        withSomeToolingModelBuilderPluginThatPerformsDependencyResolutionInBuildSrc()
        settingsFile """
            include("a")
            include("b")
        """
        buildFile "a/build.gradle", """
            plugins.apply(my.MyPlugin)
            dependencies {
                implementation(project(":b"))
            }
        """
        buildFile "b/build.gradle", """
            plugins.apply(my.MyPlugin)
        """

        when:
        withIsolatedProjects()
        def model = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model.size() == 2
        fixture.assertModelStored {
            projectConfigured(":buildSrc")
            projectConfigured(":")
            buildModelCreated()
            modelsCreated(":a", ":b")
        }

        when:
        buildFile "a/build.gradle", """
            plugins.apply(my.MyPlugin)
        """
        corruptReusedProjectState()
        withIsolatedProjects()
        runBuildActionFails(new FetchCustomModelForEachProject())

        then:
        failureCauseContains("Could not load entry for")

        when:
        withIsolatedProjects()
        def model2 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model2.size() == 2
        fixture.assertModelStored {
            projectConfigured(":buildSrc")
            projectConfigured(":")
            buildModelCreated()
            modelsCreated(":a", ":b")
        }
    }

    def "does not discard a corrupted reused project state entry when recovery is disabled"() {
        given:
        withSomeToolingModelBuilderPluginThatPerformsDependencyResolutionInBuildSrc()
        settingsFile """
            include("a")
            include("b")
        """
        buildFile "a/build.gradle", """
            plugins.apply(my.MyPlugin)
            dependencies {
                implementation(project(":b"))
            }
        """
        buildFile "b/build.gradle", """
            plugins.apply(my.MyPlugin)
        """

        when:
        withIsolatedProjects(DISABLE_CC_RECOVERY)
        runBuildAction(new FetchCustomModelForEachProject())

        then:
        fixture.assertModelStored {
            projectConfigured(":buildSrc")
            projectConfigured(":")
            buildModelCreated()
            modelsCreated(":a", ":b")
        }

        when:
        buildFile "a/build.gradle", """
            plugins.apply(my.MyPlugin)
        """
        corruptReusedProjectState()
        withIsolatedProjects(DISABLE_CC_RECOVERY)
        runBuildActionFails(new FetchCustomModelForEachProject())

        then:
        failureCauseContains("Could not load entry for")

        when:
        withIsolatedProjects(DISABLE_CC_RECOVERY)
        runBuildActionFails(new FetchCustomModelForEachProject())

        then:
        failureCauseContains("Could not load entry for")
    }

    def "reports the model failure alongside the task failure when a corrupted entry is not recovered"() {
        given:
        withSomeToolingModelBuilderPluginInBuildSrc()
        settingsFile """
            include("a")
        """
        buildFile """
            plugins.apply(my.MyPlugin)
            def projectPath = project.path
            tasks.register("hello") { doLast { println("hello from " + projectPath) } }
        """
        buildFile "a/build.gradle", """
            plugins.apply(my.MyPlugin)
            def projectPath = project.path
            tasks.register("hello") { doLast { println("hello from " + projectPath) } }
        """

        when:
        withIsolatedProjects(DISABLE_CC_RECOVERY)
        runBuildAction(new FetchCustomModelForEachProject()) { forTasks("hello") }

        then:
        outputContains("hello from :")
        outputContains("hello from :a")

        when:
        corruptStoredState()
        withIsolatedProjects(DISABLE_CC_RECOVERY)
        executer.withStackTraceChecksDisabled()
        runBuildActionFails(new FetchCustomModelForEachProject()) { forTasks("hello") }

        then:
        failure.assertHasFailures(2)
        failure.assertHasErrorOutput("BuildAction failed with an exception.")
        failure.assertHasDescription("reached end of stream after reading 3 bytes; 16 bytes expected")
        failure.assertHasErrorOutput("Task failed with an exception.")
        failure.assertHasDescription("reached end of stream after reading 7 bytes; 16 bytes expected")
    }

    private void corruptStoredState() {
        def entryDir = configurationCacheDir.listFiles().find { it.directory && it.file("entry.bin").exists() }
        def keep = ['entry.bin', 'buildfingerprint.bin', 'projectfingerprint.bin', 'classloaderscopes.bin']
        def stateFiles = entryDir.listFiles().findAll { it.name.endsWith(".bin") && !(it.name in keep) }
        assert stateFiles.any { it.name.endsWith(".work.bin") }
        assert stateFiles.any { !it.name.endsWith(".work.bin") }
        stateFiles.each {
            it.bytes = (it.name.endsWith(".work.bin") ? "corrupt" : "bad").bytes
        }
    }

    private void corruptReusedProjectState() {
        def entryDir = configurationCacheDir.listFiles().find { it.directory && it.file("entry.bin").exists() }
        def stateFiles = entryDir.listFiles().findAll { it.name.startsWith("projectmetadata") }
        assert !stateFiles.empty
        stateFiles.each { it.bytes = "corrupt".bytes }
    }
}
