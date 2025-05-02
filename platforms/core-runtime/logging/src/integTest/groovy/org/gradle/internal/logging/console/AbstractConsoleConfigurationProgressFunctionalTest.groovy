/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.logging.console

import org.gradle.api.logging.configuration.ConsoleOutput
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.RichConsoleStyling
import org.gradle.integtests.fixtures.executer.GradleHandle
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule

abstract class AbstractConsoleConfigurationProgressFunctionalTest extends AbstractIntegrationSpec {
    @Rule
    BlockingHttpServer server = new BlockingHttpServer()
    GradleHandle gradle

    def setup() {
        executer.withConsole(consoleType)
        server.start()
    }

    abstract ConsoleOutput getConsoleType()

    def "shows work in progress as projects are configured"() {
        createDirs("a", "b", "c", "d")
        settingsFile << """
            include "a", "b", "c", "d"
        """
        buildFile << """
            ${server.callFromBuild('root-build-script')}
            task hello
        """
        file("b/build.gradle") << """
            ${server.callFromBuild('b-build-script')}
        """

        given:
        def rootBuildScript = server.expectAndBlock('root-build-script')
        def bBuildScript = server.expectAndBlock('b-build-script')
        gradle = executer.withTasks("hello").start()

        expect:
        rootBuildScript.waitForAllPendingCalls()
        assertHasWorkInProgress("root project")
        rootBuildScript.releaseAll()

        and:
        bBuildScript.waitForAllPendingCalls()
        assertHasWorkInProgress(":b")
        bBuildScript.releaseAll()

        and:
        gradle.waitForFinish()
    }

    def "shows work in progress with included build"() {
        createDirs("child")
        settingsFile << """
            includeBuild "child"
        """
        buildFile << """
            ${server.callFromBuild('root-build-script')}
            task hello {
                dependsOn gradle.includedBuild("child").task(":hello")
            }
        """
        createDirs("child/a", "child/b")
        file("child/settings.gradle") << """
            include 'a', 'b'
        """
        file("child/build.gradle") << """
            ${server.callFromBuild('child-build-script')}
            task hello
        """
        file("child/a/build.gradle") << """
            ${server.callFromBuild('child-a-build-script')}
            task hello
        """

        given:
        def childBuildScript = server.expectAndBlock('child-build-script')
        def childABuildScript = server.expectAndBlock('child-a-build-script')
        def rootBuildScript = server.expectAndBlock('root-build-script')
        gradle = executer.withTasks("hello").start()

        expect:
        childBuildScript.waitForAllPendingCalls()
        assertHasWorkInProgress(":child")
        childBuildScript.releaseAll()

        and:
        childABuildScript.waitForAllPendingCalls()
        assertHasWorkInProgress(":child:a")
        childABuildScript.releaseAll()

        and:
        rootBuildScript.waitForAllPendingCalls()
        assertHasWorkInProgress("root project")
        rootBuildScript.releaseAll()

        and:
        gradle.waitForFinish()
    }

    def "shows work in progress with buildSrc build"() {
        buildFile << """
            ${server.callFromBuild('root-build-script')}
            task hello
        """
        createDirs("buildSrc", "buildSrc/a", "buildSrc/b")
        file("buildSrc/settings.gradle") << """
            include 'a', 'b'
        """
        file("buildSrc/build.gradle") << """
            ${server.callFromBuild('buildsrc-build-script')}
        """
        file("buildSrc/a/build.gradle") << """
            ${server.callFromBuild('buildsrc-a-build-script')}
        """

        given:
        def childBuildScript = server.expectAndBlock('buildsrc-build-script')
        def childABuildScript = server.expectAndBlock('buildsrc-a-build-script')
        def rootBuildScript = server.expectAndBlock('root-build-script')
        gradle = executer.withTasks("hello").start()

        expect:
        childBuildScript.waitForAllPendingCalls()
        assertHasWorkInProgress("Building buildSrc > :buildSrc")
        childBuildScript.releaseAll()

        and:
        childABuildScript.waitForAllPendingCalls()
        assertHasWorkInProgress("Building buildSrc > :buildSrc:a")
        childABuildScript.releaseAll()

        and:
        rootBuildScript.waitForAllPendingCalls()
        assertHasWorkInProgress("root project")
        rootBuildScript.releaseAll()

        and:
        gradle.waitForFinish()
    }

    void assertHasWorkInProgress(String message) {
        ConcurrentTestUtil.poll {
            RichConsoleStyling.assertHasWorkInProgress(gradle, "> " + message)
        }
    }
}
