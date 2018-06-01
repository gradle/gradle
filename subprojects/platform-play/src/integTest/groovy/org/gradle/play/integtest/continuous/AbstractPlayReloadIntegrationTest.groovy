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
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule

abstract class AbstractPlayReloadIntegrationTest extends AbstractMultiVersionPlayReloadIntegrationTest {
    @Rule BlockingHttpServer server = new BlockingHttpServer()

    RunningPlayApp runningApp = new AdvancedRunningPlayApp(testDirectory)
    PlayApp playApp = new AdvancedPlayApp()

    def cleanup() {
        stopGradle()
        if (runningApp.isInitialized()) {
            appIsStopped()
        }
    }

    protected void addNewRoute(String route) {
        file("conf/routes") << "\nGET     /${route}                   @controllers.Application.${route}"
        file("app/controllers/Application.scala").with {
            text = text.replaceFirst(/(?s)\}\s*$/, """
  def ${route} = Action {
    Ok("${route} world")
  }
}
""")
        }
    }

    protected errorPageHasTaskFailure(task) {
        def error = runningApp.playUrlError()
        assert error.httpCode == 500
        assert error.text.contains("Gradle Build Failure")
        assert error.text.contains("Execution failed for task &#x27;:$task&#x27;.")
        error
    }

    protected void addBadCode() {
        file("conf/routes") << "\nGET     /hello                   @controllers.Application.hello"
        file("app/controllers/Application.scala").with {
            text = text.replaceFirst(/(?s)\}\s*$/, '''
  def hello = Action {
    Ok("hello world")
  }
''') // missing closing brace
        }
    }

    protected void fixBadCode() {
        file("app/controllers/Application.scala") << "}"
    }

    protected void addPendingChangesHook() {
        buildFile << """
            def pendingChangesManager = gradle.services.get(${PendingChangesManager.canonicalName})
            pendingChangesManager.addListener {
                def reportChangesMarker = file('report-changes')
                if (reportChangesMarker.exists()) {
                    Thread.start {
                        ${server.callFromBuild("pendingChange")}
                    }
                    reportChangesMarker.delete()
                }
            }

            gradle.taskGraph.afterTask { task ->
                if (task.path != ":compilePlayBinaryScala") {
                    return
                }

                def waitForChangesMarker = file('wait-for-changes')
                if (waitForChangesMarker.exists()) {
                    println "WAITING FOR CHANGES"
                    def pendingChanges = new java.util.concurrent.atomic.AtomicBoolean(false)
                    def pendingChangesListener = new ${SingleFirePendingChangesListener.canonicalName}({
                        synchronized(pendingChanges) {
                            println "Pending changes detected"
                            pendingChanges.set(true)
                            pendingChanges.notifyAll()
                        }
                    } as ${PendingChangesListener.canonicalName})

                    pendingChangesManager.addListener pendingChangesListener
                    
                    // Signal we are listening for changes
                    ${server.callFromBuild("rebuild")}

                    synchronized(pendingChanges) {
                        while(!pendingChanges.get()) {
                            pendingChanges.wait()
                        }
                    }
                    
                    waitForChangesMarker.delete()
                }
            }
        """

    }

    def blockBuildWaitingForChanges() {
        file('wait-for-changes').touch()
        return server.expectAndBlock("rebuild")
    }

    def changesReported() {
        file('report-changes').touch()
        return server.expectAndBlock("pendingChange")
    }
}
