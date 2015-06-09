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

import org.gradle.play.integtest.fixtures.AbstractPlayContinuousBuildIntegrationTest
import org.gradle.play.integtest.fixtures.RunningPlayApp
import org.gradle.play.integtest.fixtures.app.AdvancedPlayApp
import org.gradle.play.integtest.fixtures.app.PlayApp
import spock.lang.Ignore

class PlayReloadIntegrationTest extends AbstractPlayContinuousBuildIntegrationTest {
    RunningPlayApp runningApp = new RunningPlayApp(testDirectory)
    PlayApp playApp = new AdvancedPlayApp()

    def cleanup() {
        stopGradle()
        assert appIsStopped()
    }

    def "can modify play app while app is running in continuous build"() {
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

    def "can modify play app before it has been started"() {
        when:
        addHelloWorld()
        succeeds("runPlayBinary")

        then:
        appIsRunningAndDeployed()
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

    def "can reload java controller"() {
        when:
        succeeds("runPlayBinary")

        then:
        appIsRunningAndDeployed()

        when:
        file("conf/jva.routes") << "\nGET     /hello                   controllers.jva.PureJava.hello"
        file("app/controllers/jva/PureJava.java").with {
            text = text.replaceFirst(/(?s)\}\s*$/, '''
  public static Result hello() {
    return ok("Hello world from Java");
  }
}
''')
        }

        then:
        succeeds()
        runningApp.playUrl('java/hello').text == 'Hello world from Java'
    }

    @Ignore
    def "minify works properly"() {
        when:
        succeeds("runPlayBinary")

        then:
        appIsRunningAndDeployed()
        !runningApp.playUrl('assets/javascripts/test.js').text.contains('Hello coffeescript')
        !runningApp.playUrl('assets/javascripts/test.min.js').text.contains('Hello coffeescript')

        when:
        stopGradle()
        file("app/assets/javascripts/test.coffee") << '''
message = "Hello coffeescript"
'''
        succeeds("runPlayBinary")

        then:
        appIsRunningAndDeployed()
        runningApp.playUrl('assets/javascripts/test.js').text.contains('Hello coffeescript')
        runningApp.playUrl('assets/javascripts/test.min.js').text.contains('Hello coffeescript')
    }


    def "can modify coffeescript file"() {
        when:
        succeeds("runPlayBinary")

        then:
        appIsRunningAndDeployed()
        !runningApp.playUrl('assets/javascripts/test.js').text.contains('Hello coffeescript')
        !runningApp.playUrl('assets/javascripts/test.min.js').text.contains('Hello coffeescript')

        when:
        file("app/assets/javascripts/test.coffee") << '''
message = "Hello coffeescript"
'''

        then:
        succeeds()
        runningApp.playUrl('assets/javascripts/test.js').text.contains('Hello coffeescript')
        // TODO: fix bug in minify task first
        //runningApp.playUrl('assets/javascripts/test.min.js').text.contains('Hello coffeescript')
    }

    def "can add javascript file"() {
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
}
