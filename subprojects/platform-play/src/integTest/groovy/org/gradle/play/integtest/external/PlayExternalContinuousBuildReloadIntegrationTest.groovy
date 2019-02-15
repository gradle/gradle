/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.play.integtest.external

import org.gradle.internal.filewatch.PendingChangesManager
import org.gradle.play.integtest.fixtures.external.AbstractPlayExternalReloadIntegrationTest
import org.gradle.test.fixtures.ConcurrentTestUtil

/**
 * Test Play reload with `--continuous`
 */
class PlayExternalContinuousBuildReloadIntegrationTest extends AbstractPlayExternalReloadIntegrationTest {

    protected static final String PENDING_DETECTED_MESSAGE = 'Pending changes detected'

    int pendingChangesMarker

    def setup() {
        buildFile << """
                def pendingChangesManager = gradle.services.get(${PendingChangesManager.canonicalName})
                pendingChangesManager.addListener {
                    println "$PENDING_DETECTED_MESSAGE"
                }
        """
        buildFile << playLogbackDependenciesIfPlay25(versionNumber)
    }

    protected int waitForChangesToBePickedUp() {
        waitForConditionSatisfied { output -> output.contains(PENDING_DETECTED_MESSAGE) }
    }

    protected int waitForBuildFinish() {
        waitForConditionSatisfied { output -> output ==~ /(?s).*Waiting for changes.*/ }
    }

    private int waitForConditionSatisfied(Closure predicate){
        def buildOutput = ''
        ConcurrentTestUtil.poll {
            buildOutput = buildOutputSoFar()
            assert predicate(buildOutput.substring(pendingChangesMarker))
        }
        pendingChangesMarker = buildOutput.length()
    }

    def "should reload modified scala controller and routes and restart server"() {
        when:
        succeeds("runPlay")

        then:
        appIsRunningAndDeployed()

        when:
        addNewRoute('hello')
        waitForChangesToBePickedUp()
        def page = runningApp.playUrl('hello').text
        serverRestart()

        then:
        page == 'hello world'
    }

    def "should reload with exception when modify scala controller and restart server"() {
        when:
        succeeds("runPlay")
        then:
        appIsRunningAndDeployed()

        when:
        addBadCode()
        waitForChangesToBePickedUp()

        then:
        println "CHECKING ERROR PAGE"
        errorPageHasTaskFailure("compileScala")
        waitForBuildFinish()
        serverStartCount == 1
        !executedTasks.contains('runPlay')

        when:
        fixBadCode()
        waitForChangesToBePickedUp()
        runningApp.playUrl().text
        serverRestart()
        println "CHANGES DETECTED IN BUILD"

        then:
        appIsRunningAndDeployed()
    }

    def "should detect new javascript files but not restart"() {
        when:
        succeeds("runPlay")

        then:
        appIsRunningAndDeployed()

        when:
        file("app/assets/javascripts/helloworld.js") << '''
var message = "Hello JS";
'''
        waitForChangesToBePickedUp()

        def helloworldJs = runningApp.playUrl('assets/javascripts/helloworld.js').text
        def helloworldMinJs = runningApp.playUrl('assets/javascripts/helloworld.min.js').text
        noServerRestart()

        then:
        helloworldJs.contains('Hello JS')
        helloworldMinJs.contains('Hello JS')
    }

    def "should reload modified java model and restart server"() {
        when:
        succeeds("runPlay")

        then:
        appIsRunningAndDeployed()
        assert runningApp.playUrl().text.contains("<li>foo:1</li>")

        when:
        file("app/models/DataType.java").with {
            text = text.replaceFirst(~/"%s:%s"/, '"Hello %s:%s !"')
        }
        waitForChangesToBePickedUp()

        def page = runningApp.playUrl().text
        serverRestart()

        then:
        page.contains("<li>Hello foo:1 !</li>")
    }

    def "should reload twirl template and restart server"() {
        when:
        succeeds("runPlay")

        then:
        appIsRunningAndDeployed()

        when:
        file("app/views/index.scala.html").with {
            text = text.replaceFirst(~/Welcome to Play/, 'Welcome to Play with Gradle')
        }
        waitForChangesToBePickedUp()
        def page = runningApp.playUrl().text
        serverRestart()

        then:
        page.contains("Welcome to Play with Gradle")
    }

    def "should reload with exception when task that depends on runPlay fails"() {
        given:
        buildFile << """
task otherTask {
   dependsOn 'runPlay'
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
        addNewRoute('hello')
        waitForChangesToBePickedUp()

        then:
        errorPageHasTaskFailure("otherTask")
        !executedTasks.contains('runPlay')
    }
}
