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

class PlayReloadIntegrationTest extends AbstractPlayContinuousBuildIntegrationTest {
    RunningPlayApp runningApp = new RunningPlayApp(testDirectory)
    PlayApp playApp = new AdvancedPlayApp()

    def cleanup() {
        stopGradle()
        assert appIsStopped()
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

    def "should reload modified java controller and routes"() {
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

    def "should reload modified scala model"() {
        when:
        succeeds("runPlayBinary")

        then:
        appIsRunningAndDeployed()
        assert runningApp.playUrl().text.contains("<li>foo:1</li>")

        when:
        file("conf/scala.routes") << "\nGET     /hello                   controllers.scala.MixedJava.hello"
        file("app/models/ScalaClass.scala") << '''{
    def hello() = {
        "Hello " + name  + " from scala model"
    }
}
'''
        file("app/controllers/scala/MixedJava.java").with {
            text = text.replaceFirst(/(?s)\}\s*$/, '''
  public static Result hello() {
    return ok(new models.ScalaClass("world").hello());
  }
}
''')
        }

        then:
        succeeds()
        assert runningApp.playUrl("scala/hello").text.contains("Hello world from scala model")
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

    def "should reload css"() {
        when:
        succeeds("runPlayBinary")

        then:
        appIsRunningAndDeployed()

        when:
        file("public/stylesheets/main.css") << 'body { font-size: 20px }'

        then:
        succeeds()
        assert runningApp.playUrl('assets/stylesheets/main.css').text.contains("body { font-size: 20px }")
    }
}
