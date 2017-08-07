/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.play.integtest.continuous

import org.gradle.internal.filewatch.DefaultFileSystemChangeWaiterFactory
import org.gradle.language.scala.tasks.PlatformScalaCompile
import org.gradle.test.fixtures.ConcurrentTestUtil
import spock.lang.Unroll

class PlayReloadWaitingIntegrationTest extends PlayReloadIntegrationTest {

    def "wait for changes to be built when a request comes in during a build"() {
        server.start()
        file('hooks.gradle') << """
            gradle.projectsLoaded {
                ${server.callFromBuild("buildStarted")}
            }
            gradle.buildFinished {
                ${server.callFromBuild("buildFinished")}
            }
        """
        executer.withArguments("-I", file("hooks.gradle").absolutePath)
        when:
        server.expect("buildStarted")
        server.expect("buildFinished")
        succeeds("runPlayBinary")
        then:
        appIsRunningAndDeployed()

        when:
        file("conf/routes") << "\nGET     /hello                   controllers.Application.hello"
        file("app/controllers/Application.scala").with {
            text = text.replaceFirst(/(?s)\}\s*$/, """
  def hello = Action {
    println(scala.io.Source.fromURL("${server.uri("playApp")}").mkString)
    Ok("hello world")
  }
}
""")
        }
        then:
        def block = server.expectAndBlock( "buildStarted")
        block.waitForAllPendingCalls()
        block.releaseAll()
        and:
        server.expect("buildFinished")
        // Play app should respond after the build is complete
        server.expect("playApp")
        ConcurrentTestUtil.poll {
            assert runningApp.playUrl('hello').text == 'hello world'
        }
    }

    @Unroll
    def "wait for changes to be built when a request comes in during initial app startup and there are pending changes and build is gated=#gated"() {
        addPendingChangesHook()
        // Prebuild so we don't timeout waiting for the 'rebuild' trigger
        executer.withTasks("playBinary").run()
        executer.withArgument("-D" + DefaultFileSystemChangeWaiterFactory.GATED_BUILD_SYSPROP + "=" + gated)

        server.start()
        buildFile << """
            tasks.withType(${PlatformScalaCompile.name}) {
                doLast {
                    def routes = file('conf/routes').text
                    if (!routes.contains("hello")) {
                        addPendingChangeListener()
                        // signal we're ready to modify
                        ${server.callFromBuild("rebuild")}
                        // test should have added the hello route, so wait until Gradle
                        // detects this as a pending change
                        waitForPendingChanges()
                    }
                }
            }
        """

        when:
        start("runPlayBinary")
        def rebuild = server.expectAndBlock("rebuild")
        rebuild.waitForAllPendingCalls()
        // Trigger a change during the build
        addNewRoute()
        rebuild.releaseAll()
        then:
        appIsRunningAndDeployed()
        runningApp.playUrl('hello').text == 'hello world'

        where:
        gated << [true, false ]
    }

    def "wait for pending changes to be built if a request comes in during a build and there are pending changes"() {
        server.start()
        addPendingChangesHook()
        buildFile << """
            tasks.withType(${PlatformScalaCompile.name}) {
                doLast {
                    def routes = file('conf/routes').text
                    if (routes.contains("hello") && !routes.contains("goodbye")) {
                        addPendingChangeListener()
                        // hello route is present, signal we're ready to modify again
                        ${server.callFromBuild("rebuild")}
                        // test should have added the goodbye route, so wait until Gradle
                        // detects this as a pending change
                        waitForPendingChanges()
                    }
                }
            }
        """

        when:
        succeeds("runPlayBinary")
        then:
        appIsRunningAndDeployed()

        when:
        // Trigger a change
        addNewRoute()
        def rebuild = server.expectAndBlock("rebuild")
        rebuild.waitForAllPendingCalls()
        // Trigger another change during the build
        addNewRoute("goodbye")
        rebuild.releaseAll()
        then:
        // goodbye route is added by second change, so if it's available, we know we've blocked
        runningApp.playUrl('goodbye').text == 'goodbye world'
        runningApp.playUrl('hello').text == 'hello world'
    }

    def "wait for pending changes to be built if a request comes in during a failing build and there are pending changes"() {
        server.start()
        addPendingChangesHook()
        buildFile << """
            gradle.taskGraph.afterTask { task ->
                if (task.path != ":compilePlayBinaryScala") {
                    return
                }
                def routes = file('conf/routes').text
                if (routes.contains("hello") && task.state.failure) {
                    addPendingChangeListener()
                    // hello route is present, signal we're ready to modify again
                    ${server.callFromBuild("rebuild")}
                    // test should have added the goodbye route, so wait until Gradle
                    // detects this as a pending change
                    waitForPendingChanges()
                }
            }
        """

        when:
        succeeds("runPlayBinary")
        then:
        appIsRunningAndDeployed()

        when:
        // Trigger a change that breaks the build
        addBadCode()
        def rebuild = server.expectAndBlock("rebuild")
        rebuild.waitForAllPendingCalls()
        // Trigger another change during the build that works
        fixBadCode()
        rebuild.releaseAll()
        then:
        runningApp.playUrl('hello').text == 'hello world'
    }
}
