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
import org.gradle.play.integtest.fixtures.MultiProjectRunningPlayApp
import org.gradle.play.integtest.fixtures.PlayApp
import org.gradle.play.integtest.fixtures.RunningPlayApp
import org.gradle.play.integtest.fixtures.app.PlayMultiProject
import org.gradle.test.fixtures.file.TestFile

class PlayMultiProjectReloadIntegrationTest extends AbstractMultiVersionPlayReloadIntegrationTest {
    RunningPlayApp runningApp = new MultiProjectRunningPlayApp(testDirectory)
    PlayApp playApp = new PlayMultiProject()
    TestFile playRunBuildFile = file("primary/build.gradle")

    def cleanup() {
        stopGradle()
        if (runningApp.isInitialized()) {
            appIsStopped()
        }
    }

    def "can modify play app while app is running in continuous build and server restarts"() {
        when:
        succeeds(":primary:runPlayBinary")

        then:
        appIsRunningAndDeployed()

        when:
        addHelloWorld()
        succeeds()
        def page = runningApp.playUrl('hello').text
        serverRestart()

        then:
        page == 'Hello world'
    }

    private void addHelloWorld() {
        file("primary/conf/routes") << "\nGET     /hello                   @controllers.Application.hello"
        file("primary/app/controllers/Application.scala").with {
            text = text.replaceFirst(/(?s)\}\s*$/, '''
  def hello = Action {
    Ok("Hello world")
  }
}
''')
        }
    }

    def "can modify sub module in multi-project play app while app is running in continuous build and server restarts"() {
        when:
        succeeds(":primary:runPlayBinary")

        then:
        appIsRunningAndDeployed()

        when:
        addSubmoduleHelloWorld()
        succeeds()
        def page = runningApp.playUrl('subhello').text
        serverRestart()

        then:
        page == 'Hello world'
    }

    private void addSubmoduleHelloWorld() {
        file("primary/conf/routes") << "\nGET     /subhello                   @controllers.submodule.Application.hello"
        file("submodule/app/controllers/submodule/Application.scala").with {
            text = text.replaceFirst(/(?s)\}\s*$/, '''
  def hello = Action {
    Ok("Hello world")
  }
}
''')
        }
    }

    def "can modify java sub module in multi-project play app while app is running in continuous build and server restarts"() {
        when:
        succeeds(":primary:runPlayBinary")

        then:
        appIsRunningAndDeployed()

        when:
        addSubmoduleHelloWorldFromJavaClass()
        succeeds()
        def page = runningApp.playUrl('subhello').text
        serverRestart()

        then:
        page == 'Hello from Java!'
    }

    private void addSubmoduleHelloWorldFromJavaClass() {
        file("primary/conf/routes") << "\nGET     /subhello                   @controllers.submodule.Application.hello"
        file("submodule/app/controllers/submodule/Application.scala").with {
            text = text.replaceFirst(~/(?m)^import\s/, '''
import org.test.Util
$0''')
            text = text.replaceFirst(/(?s)\}\s*$/, '''
  def hello = Action {
    Ok(Util.hello())
  }
}
''')
        }
        file("javalibrary/src/main/java/org/test/Util.java").with {
            text = text.replaceFirst(/(?s)\}\s*$/, '''
  public static String hello() {
    return "Hello from Java!";
  }
}
''')
        }
        file("submodule/build.gradle") << '''
dependencies {
    play project(":javalibrary")
}
'''
    }

    def "can add javascript file to primary project and server does not restart"() {
        when:
        succeeds(":primary:runPlayBinary")

        then:
        appIsRunningAndDeployed()

        when:
        file("primary/public/helloworld.js") << '''
var message = "Hello JS";
'''
        succeeds()
        def js = runningApp.playUrl('assets/helloworld.js').text
        noServerRestart()

        then:
        js.contains('Hello JS')
    }

    def "should reload with exception when modify java in submodule and server restarts"() {
        when:
        succeeds(":primary:runPlayBinary")
        then:
        appIsRunningAndDeployed()

        when:
        addBadScala("submodule/app")

        then:
        fails()
        !executedTasks.contains(':primary:runPlayBinary')
        errorPageHasTaskFailure(":submodule:compilePlayBinaryScala")
        serverStartCount == 1

        when:
        fixBadScala("submodule/app")

        then:
        succeeds()
        appIsRunningAndDeployed()
        runningApp.playUrl().text
        serverRestart()
    }

    def addBadScala(path) {
        file("$path/models/NewType.scala") << """
package models

object NewType {
"""
    }

    def fixBadScala(path) {
        file("$path/models/NewType.scala") << """
}
"""
    }

    def "can add javascript file to sub module and server restarts"() {
        when:
        succeeds(":primary:runPlayBinary")

        then:
        appIsRunningAndDeployed()

        when:
        file("submodule/public/helloworld.js") << '''
var message = "Hello from submodule";
'''
        succeeds()
        def js = runningApp.playUrl('assets/helloworld.js').text
        serverRestart()

        then:
        js.contains('Hello from submodule')
    }

    private errorPageHasTaskFailure(task) {
        def error = runningApp.playUrlError()
        assert error.httpCode == 500
        assert error.text.contains("Gradle Build Failure")
        assert error.text.contains("Execution failed for task &#x27;$task&#x27;.")
        error
    }
}
