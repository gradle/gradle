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

package org.gradle.api.internal.project

import org.gradle.api.Project
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.dsl.GradleDsl
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions

class ProjectIdentityIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        file("buildSrc/src/main/kotlin/LifetimeKeeper.kt") << """
            import ${WeakHashMap.name}
            import ${Project.name}

            object LifetimeKeeper {
                val map = WeakHashMap<Project, Unit>()
            }
        """
        file("buildSrc/src/main/kotlin/lifetime.gradle.kts") << """
            if (LifetimeKeeper.map.put(project.rootProject, Unit) == null) {
                println("Action executed: \${project.name}")
            }
        """
        file("buildSrc/build.gradle.kts") << """
            plugins {
                `kotlin-dsl`
            }

            ${mavenCentralRepository(GradleDsl.KOTLIN)}
        """
    }

    @Requires(value = IntegTestPreconditions.NotIsolatedProjects, reason = "IP enables parallel configuration")
    def 'project lifetime is same as build'() {
        given:
        settingsFile """
            include(":a")
            include(":b")
        """

        buildFile("a/build.gradle", """
            plugins {
                id("lifetime")
            }
            System.gc()
        """)

        buildFile("b/build.gradle", """
            plugins {
                id("lifetime")
            }
            System.gc()
        """)

        when:
        run "help"

        then:
        outputContains("Action executed: a")
        outputDoesNotContain("Action executed: b")
    }

    @Requires(IntegTestPreconditions.NotConfigCached)
    def 'project identity doesnt survive build finish'() {
        given:
        executer.requireIsolatedDaemons()
        settingsFile """
            rootProject.name = "root"
        """
        buildFile """
            plugins {
                id("lifetime")
            }
        """

        when:
        run "help"

        then:
        outputContains("Action executed: root")

        when:
        run "help"

        then:
        outputContains("Action executed: root")
    }
}
