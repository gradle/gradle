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
import org.gradle.play.integtest.fixtures.RunningPlayApp
import org.gradle.play.integtest.fixtures.app.AdvancedPlayApp
import org.gradle.play.integtest.fixtures.app.PlayApp

class PlayReloadIntegrationTest extends AbstractMultiVersionPlayReloadIntegrationTest {
    RunningPlayApp runningApp = new RunningPlayApp(testDirectory)
    PlayApp playApp = new AdvancedPlayApp()

    def cleanup() {
        stopGradle()
        appIsStopped()
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

    def "should reload with exception when modify routes"() {
        when:
        succeeds("runPlayBinary")
        then:
        appIsRunningAndDeployed()

        when:
        addBadRoute()
        then:
        fails()
        !executedTasks.contains('runPlayBinary')
        errorPageHasTaskFailure("compilePlayBinaryRoutes")

        when:
        fixBadRoute()
        then:
        succeeds()
        appIsRunningAndDeployed()
    }

    def addBadRoute() {
        file("conf/routes") << "\nGET     /badroute"
    }

    def fixBadRoute() {
        file("conf/routes") << " controllers.Application.index"
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

    def "should reload with exception when modify CoffeeScript"() {
        when:
        succeeds("runPlayBinary")
        then:
        appIsRunningAndDeployed()

        when:
        addBadCoffeeScript()

        then:
        fails()
        !executedTasks.contains('runPlayBinary')
        errorPageHasTaskFailure("compilePlayBinaryCoffeeScript")

        when:
        fixBadCoffeeScript()
        then:
        succeeds()
        appIsRunningAndDeployed()
    }

    def addBadCoffeeScript() {
        // missing closing quote
        file("app/assets/javascripts/bad.coffee") << """
message = "Hello CoffeeScript"""
    }

    def fixBadCoffeeScript() {
        file("app/assets/javascripts/bad.coffee") << """ "
alert message
"""
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

    def "should reload with exception when modify javascript"() {
        when:
        succeeds("runPlayBinary")
        then:
        appIsRunningAndDeployed()

        when:
        addBadJavaScript()

        then:
        fails()
        !executedTasks.contains('runPlayBinary')
        errorPageHasTaskFailure("minifyPlayBinaryJavaScript")

        when:
        fixBadJavaScript()
        then:
        succeeds()
        appIsRunningAndDeployed()
    }

    def addBadJavaScript() {
        // missing closing quote
        file("app/assets/javascripts/bad.js") << '''
var message = "Hello JS'''
    }

    def fixBadJavaScript() {
        file("app/assets/javascripts/bad.js") << """ ";
alert(message);
"""
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

    def "should reload with exception when modify java"() {
        when:
        succeeds("runPlayBinary")
        then:
        appIsRunningAndDeployed()

        when:
        addBadJava()

        then:
        fails()
        !executedTasks.contains('runPlayBinary')
        errorPageHasTaskFailure("compilePlayBinaryScala")

        when:
        fixBadJava()
        then:
        succeeds()
        appIsRunningAndDeployed()
    }

    def addBadJava() {
        file("app/models/NewType.java") << """
package models;

public class NewType {
"""
    }

    def fixBadJava() {
        file("app/models/NewType.java") << """
}
"""
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


    def "should reload with exception when modify twirl template"() {
        when:
        succeeds("runPlayBinary")
        then:
        appIsRunningAndDeployed()

        when:
        addBadTwirlTemplate()

        then:
        fails()
        !executedTasks.contains('runPlayBinary')
        errorPageHasTaskFailure("compilePlayBinaryTwirlTemplates")

        when:
        fixBadTwirlTemplate()
        then:
        succeeds()
        appIsRunningAndDeployed()
    }

    def addBadTwirlTemplate() {
        file("app/views/bad.scala.html") << """
@(name: String)
<!DOCTYPE html>
<html>
<head></head>
<body>
Hello @{name"""
    }

    def fixBadTwirlTemplate() {
        file("app/views/bad.scala.html") << """}!
</body>
</html>
"""
    }
}
