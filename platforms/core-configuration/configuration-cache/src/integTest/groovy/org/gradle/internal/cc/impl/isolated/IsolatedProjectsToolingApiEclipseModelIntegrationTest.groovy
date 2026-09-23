/*
 * Copyright 2026 the original author or authors.
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

import org.gradle.tooling.model.eclipse.EclipseProject

/**
 * Covers the Eclipse tooling models that Buildship requests during project synchronization.
 */
class IsolatedProjectsToolingApiEclipseModelIntegrationTest extends AbstractIsolatedProjectsToolingApiIntegrationTest {

    def "can fetch the Eclipse model"() {
        given:
        settingsFile << """
            include("a")
            include("b")
        """
        buildFile "a/build.gradle", """
            plugins { id("java-library") }
        """
        buildFile "b/build.gradle", """
            plugins { id("java-library") }
        """

        when:
        withIsolatedProjects()
        def model = fetchModel(EclipseProject)

        then:
        model != null
        fixture.assertModelStored {
            projectsConfigured(":", ":a", ":b")
            // EclipseProject on the root, plus IsolatedGradleProjectInternal and
            // IsolatedEclipseProjectInternal per project
            modelsCreated(":", 3)
            modelsCreated(":a", 2)
            modelsCreated(":b", 2)
        }
    }

    def "can fetch the Eclipse model for a build with project dependencies"() {
        given:
        settingsFile << """
            include("a")
            include("b")
        """
        buildFile "a/build.gradle", """
            plugins { id("java-library") }
            dependencies { implementation(project(":b")) }
        """
        buildFile "b/build.gradle", """
            plugins { id("java-library") }
        """

        when:
        withIsolatedProjects()
        def model = fetchModel(EclipseProject)

        then:
        model != null
        fixture.assertModelStored {
            projectsConfigured(":", ":a", ":b")
            // EclipseProject on the root, plus IsolatedGradleProjectInternal and
            // IsolatedEclipseProjectInternal per project
            modelsCreated(":", 3)
            modelsCreated(":a", 2)
            modelsCreated(":b", 2)
        }
    }

    def "can run the Eclipse auto build tasks"() {
        given:
        settingsFile << """
            include("a")
            include("b")
        """
        buildFile """
            plugins { id("eclipse") }
            tasks.register("build1")
            eclipse { autoBuildTasks("build1") }
        """
        buildFile "a/build.gradle", """
            plugins { id("eclipse") }
            tasks.register("build2")
            eclipse { autoBuildTasks("build2") }
        """
        buildFile "b/build.gradle", """
            plugins { id("eclipse") }
        """

        when:
        withIsolatedProjects()
        runBuildAction(new RunEclipseAutoBuildTasksAction())

        then:
        outputDoesNotContain("cannot access")
    }

    def "can run the Eclipse synchronization tasks"() {
        given:
        settingsFile << """
            include("a")
            include("b")
        """
        buildFile """
            plugins { id("eclipse") }
            tasks.register("sync1")
            eclipse { synchronizationTasks("sync1") }
        """
        buildFile "a/build.gradle", """
            plugins { id("eclipse") }
            tasks.register("sync2")
            eclipse { synchronizationTasks("sync2") }
        """
        buildFile "b/build.gradle", """
            plugins { id("eclipse") }
        """

        when:
        withIsolatedProjects()
        runBuildAction(new RunEclipseSynchronizationTasksAction())

        then:
        // The model only adds the registered tasks to the start parameter; a plain build action does
        // not execute them. Task execution is covered by RunEclipseSynchronizationTasksCrossVersionSpec.
        // What matters here is that collecting them from every project no longer violates isolation.
        outputDoesNotContain("cannot access")
    }
}
