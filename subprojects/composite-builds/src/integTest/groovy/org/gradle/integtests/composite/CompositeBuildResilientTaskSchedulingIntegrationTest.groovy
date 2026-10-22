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

package org.gradle.integtests.composite

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class CompositeBuildResilientTaskSchedulingIntegrationTest extends AbstractIntegrationSpec {

    private static final String FLAG = "-Dorg.gradle.internal.resilient-task-scheduling=true"

    def setup() {
        file("ok/settings.gradle") << "rootProject.name = 'ok'"
        file("ok/build.gradle") << """
            tasks.register('ideModel') {
                doLast { println 'ok model' }
            }
        """
        file("broken/settings.gradle") << "rootProject.name = 'broken'"
        file("broken/build.gradle") << """
            throw new GradleException('boom')
        """
    }

    def "runs tasks of configured included builds when another included build fails to configure"() {
        given:
        settingsFile << """
            includeBuild('ok')
            includeBuild('broken')
        """

        when:
        fails(FLAG, ":ok:ideModel", ":broken:ideModel")

        then:
        outputContains("ok model")
        failure.assertHasFailures(1)
        failure.assertHasDescription("A problem occurred evaluating project ':broken'.")
        failure.assertHasCause("boom")
    }

    def "runs tasks of configured included builds when the root build fails to configure"() {
        given:
        settingsFile << "includeBuild('ok')"
        buildFile << """
            throw new GradleException('root boom')
        """

        when:
        fails(FLAG, ":ok:ideModel", ":help")

        then:
        outputContains("ok model")
        failure.assertHasFailures(1)
        failure.assertHasCause("root boom")
    }

    def "fails before running any task when resilient task scheduling is not enabled"() {
        given:
        settingsFile << """
            includeBuild('ok')
            includeBuild('broken')
        """

        when:
        fails(":ok:ideModel", ":broken:ideModel")

        then:
        outputDoesNotContain("ok model")
        failure.assertHasFailures(1)
        failure.assertHasCause("boom")
    }

    def "skips only the tasks that depend on a task of a broken build"() {
        given:
        settingsFile << """
            includeBuild('ok')
            includeBuild('broken')
        """
        buildFile << """
            tasks.register('needsBroken') {
                dependsOn gradle.includedBuild('broken').task(':ideModel')
                doLast { println 'needsBroken ran' }
            }
            tasks.register('rootModel') {
                doLast { println 'root model' }
            }
        """

        when:
        fails(FLAG, "--continue", ":needsBroken", ":rootModel", ":ok:ideModel")

        then:
        outputContains("ok model")
        outputContains("root model")
        outputDoesNotContain("needsBroken ran")
        failure.assertHasFailures(2)
        failure.assertHasDescription("Cannot run task ':ideModel' of build 'broken' because the build failed to configure.")
        failure.assertHasDescription("A problem occurred evaluating project ':broken'.")
        failure.assertHasCause("boom")
    }
}
