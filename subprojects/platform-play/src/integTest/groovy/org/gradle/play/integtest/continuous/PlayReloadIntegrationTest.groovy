/*
 * Copyright 2015 the original author or authors.
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

import org.gradle.internal.filewatch.PendingChangesListener
import org.gradle.internal.filewatch.PendingChangesManager
import org.gradle.internal.filewatch.SingleFirePendingChangesListener
import org.gradle.play.integtest.fixtures.AbstractMultiVersionPlayReloadIntegrationTest
import org.gradle.play.integtest.fixtures.AdvancedRunningPlayApp
import org.gradle.play.integtest.fixtures.PlayApp
import org.gradle.play.integtest.fixtures.RunningPlayApp
import org.gradle.play.integtest.fixtures.app.AdvancedPlayApp
import org.gradle.play.tasks.PlayRun
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule

class PlayReloadIntegrationTest extends AbstractMultiVersionPlayReloadIntegrationTest {
    @Rule BlockingHttpServer server = new BlockingHttpServer()

    RunningPlayApp runningApp = new AdvancedRunningPlayApp(testDirectory)
    PlayApp playApp = new AdvancedPlayApp()

    def cleanup() {
        stopGradle()
        if (runningApp.isInitialized()) {
            appIsStopped()
        }
    }

    def "should reload modified scala controller and routes"() {
        when:
        succeeds("runPlayBinary")

        then:
        appIsRunningAndDeployed()

        when:
        addNewRoute()

        then:
        succeeds()
        runningApp.playUrl('hello').text == 'hello world'
    }

    def "should reload with exception when modify scala controller"() {
        when:
        succeeds("runPlayBinary")
        then:
        appIsRunningAndDeployed()

        when:
        addBadCode()
        then:
        fails()
        !executedTasks.contains('runPlayBinary')
        errorPageHasTaskFailure("compilePlayBinaryScala")

        when:
        fixBadCode()
        then:
        succeeds()
        appIsRunningAndDeployed()

    }

    def "should reload modified coffeescript"() {
        when:
        succeeds("runPlayBinary")

        then:
        appIsRunningAndDeployed()
        !runningApp.playUrl('assets/javascripts/test.js').text.contains('Hello coffeescript')
        !runningApp.playUrl('assets/javascripts/test.min.js').text.contains('Hello coffeescript')

        when:
        file("app/assets/javascripts/test.coffee") << '''
message = "Hello coffeescript"
alert message
'''

        then:
        succeeds()
        runningApp.playUrl('assets/javascripts/test.js').text.contains('Hello coffeescript')
        runningApp.playUrl('assets/javascripts/test.min.js').text.contains('Hello coffeescript')
    }

    def "should detect new javascript files"() {
        when:
        succeeds("runPlayBinary")

        then:
        appIsRunningAndDeployed()

        when:
        file("app/assets/javascripts/helloworld.js") << '''
var message = "Hello JS";
'''

        then:
        succeeds()
        runningApp.playUrl('assets/javascripts/helloworld.js').text.contains('Hello JS')
        runningApp.playUrl('assets/javascripts/helloworld.min.js').text.contains('Hello JS')
    }

    def "should reload modified java model"() {
        when:
        succeeds("runPlayBinary")

        then:
        appIsRunningAndDeployed()
        assert runningApp.playUrl().text.contains("<li>foo:1</li>")

        when:
        file("app/models/DataType.java").with {
            text = text.replaceFirst(~/"%s:%s"/, '"Hello %s:%s !"')
        }

        then:
        succeeds()
        assert runningApp.playUrl().text.contains("<li>Hello foo:1 !</li>")
    }

    def "should reload twirl template"() {
        when:
        succeeds("runPlayBinary")

        then:
        appIsRunningAndDeployed()

        when:
        file("app/views/index.scala.html").with {
            text = text.replaceFirst(~/Welcome to Play/, 'Welcome to Play with Gradle')
        }

        then:
        succeeds()
        assert runningApp.playUrl().text.contains("Welcome to Play with Gradle")
    }

    def "should reload with exception when task that depends on runPlayBinary fails"() {
        given:
        buildFile << """
task otherTask {
   dependsOn 'runPlayBinary'
   doLast {
      // second time through this route exists
      if (file("conf/routes").text.contains("/hello")) {
         throw new GradleException("always fails")
      }
   }
}
"""
        when:
        succeeds("otherTask")
        then:
        appIsRunningAndDeployed()

        when:
        addNewRoute()

        then:
        fails()
        !executedTasks.contains('runPlayBinary')
        errorPageHasTaskFailure("otherTask")
    }

    def "wait for changes to be built when a request comes in during initial app startup and there are pending changes"() {
        addPendingChangesHook()
        // Prebuild so we don't timeout waiting for the 'rebuild' trigger
        executer.withTasks("playBinary").run()

        server.start()
        buildFile << """
            tasks.withType(${PlayRun.name}) {
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
    }

    def "wait for pending changes to be built if a request comes in during a build and there are pending changes"() {
        server.start()
        addPendingChangesHook()
        buildFile << """
            tasks.withType(${PlayRun.name}) {
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

    def "wait for pending changes to be built when a request comes in after initial startup"() {
        server.start()
        settingsFile << """
            gradle.projectsLoaded {
                ${server.callFromBuild("buildStarted")}
            }
        """
        when:
        server.expect("buildStarted")
        succeeds("runPlayBinary")
        then:
        appIsRunningAndDeployed()

        when:
        addNewRoute()
        def buildStarted = server.expectAndBlock("buildStarted")
        buildStarted.waitForAllPendingCalls()
        buildStarted.releaseAll()
        then:
        runningApp.playUrl('hello').text == 'hello world'
    }

    private void addNewRoute(String route="hello") {
        file("conf/routes") << "\nGET     /${route}                   controllers.Application.${route}"
        file("app/controllers/Application.scala").with {
            text = text.replaceFirst(/(?s)\}\s*$/, """
  def ${route} = Action {
    Ok("${route} world")
  }
}
""")
        }
    }

    private errorPageHasTaskFailure(task) {
        def error = runningApp.playUrlError()
        assert error.httpCode == 500
        assert error.text.contains("Gradle Build Failure")
        assert error.text.contains("Execution failed for task &#x27;:$task&#x27;.")
        error
    }

    private void addBadCode() {
        file("conf/routes") << "\nGET     /hello                   controllers.Application.hello"
        file("app/controllers/Application.scala").with {
            text = text.replaceFirst(/(?s)\}\s*$/, '''
  def hello = Action {
    Ok("hello world")
  }
''') // missing closing brace
        }
    }

    private void fixBadCode() {
        file("app/controllers/Application.scala") << "}"
    }

    void addPendingChangesHook() {
        buildFile << """
            ext.pendingChanges = new java.util.concurrent.atomic.AtomicBoolean(false)

            void addPendingChangeListener() {
                def pendingChangesManager = gradle.services.get(${PendingChangesManager.canonicalName})
                pendingChangesManager.addListener new ${SingleFirePendingChangesListener.canonicalName}({
                    synchronized(pendingChanges) {
                        println "Pending changes detected"
                        pendingChanges.set(true)
                        pendingChanges.notifyAll()
                    }
                } as ${PendingChangesListener.canonicalName})
            }

            void waitForPendingChanges() {
                synchronized(pendingChanges) {
                    while(!pendingChanges.get()) {
                        pendingChanges.wait()
                    }
                }
            }
        """
    }
}
