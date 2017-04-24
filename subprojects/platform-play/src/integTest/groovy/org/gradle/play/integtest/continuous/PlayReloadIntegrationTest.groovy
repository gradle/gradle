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

import org.gradle.play.integtest.fixtures.AbstractMultiVersionPlayReloadIntegrationTest
import org.gradle.play.integtest.fixtures.AdvancedRunningPlayApp
import org.gradle.play.integtest.fixtures.RunningPlayApp
import org.gradle.play.integtest.fixtures.app.AdvancedPlayApp
import org.gradle.play.integtest.fixtures.PlayApp

class PlayReloadIntegrationTest extends AbstractMultiVersionPlayReloadIntegrationTest {
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
        addHelloWorld()

        then:
        succeeds()
        runningApp.playUrl('hello').text == 'Hello world'
    }

    private void addHelloWorld() {
        file("conf/routes") << "\nGET     /hello                   controllers.Application.hello"
        file("app/controllers/Application.scala").with {
            text = text.replaceFirst(/(?s)\}\s*$/, '''
  def hello = Action {
    Ok("Hello world")
  }
}
''')
        }
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

    private errorPageHasTaskFailure(task) {
        def error = runningApp.playUrlError()
        assert error.httpCode == 500
        assert error.text.contains("Gradle Build Failure")
        assert error.text.contains("Execution failed for task &#x27;:$task&#x27;.")
        error
    }

    private void addBadCode() {
        file("app/controllers/Application.scala").with {
            text = text.replaceFirst(/(?s)\}\s*$/, '''
  def hello = Action {
    Ok("Hello world")
  }
''') // missing closing brace
        }
    }

    private void fixBadCode() {
        file("app/controllers/Application.scala") << "}"
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
        addHelloWorld()

        then:
        fails()
        !executedTasks.contains('runPlayBinary')
        errorPageHasTaskFailure("otherTask")
    }
}
