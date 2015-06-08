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
import org.gradle.play.integtest.fixtures.app.BasicPlayApp
import org.gradle.play.integtest.fixtures.app.PlayApp

class PlayReloadIntegrationTest extends AbstractPlayContinuousBuildIntegrationTest {
    RunningPlayApp runningApp = new RunningPlayApp(testDirectory)
    PlayApp playApp = new BasicPlayApp()

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

        cleanup: "stopping gradle"
        stopGradle()
        appIsStopped()
    }

    def "can modify play app before it has been started"() {
        when:
        addHelloWorld()
        succeeds("runPlayBinary")

        then:
        appIsRunningAndDeployed()
        runningApp.playUrl('hello').text == 'Hello world'

        cleanup: "stopping gradle"
        stopGradle()
        appIsStopped()
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
}
