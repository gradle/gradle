/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.play.tasks
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.TestResources
import org.gradle.integtests.fixtures.UrlValidator
import org.gradle.integtests.fixtures.executer.GradleHandle
import org.junit.Assert
import org.junit.Rule
import spock.lang.Ignore

@Ignore
class PlayRunIntegrationTest extends AbstractIntegrationSpec {
    @Rule
    public final TestResources resources = new TestResources(temporaryFolder)

    //def portFinder = org.gradle.util.AvailablePortFinder.createPrivate()
    int httpPort = 9000

    def setup(){
        buildFile << """
        plugins {
            id 'play-application'
        }

        model {
            components {
                myApp(PlayApplicationSpec)
            }
        }

        repositories{
            jcenter()
            maven{
                name = "typesafe-maven-release"
                url = "http://repo.typesafe.com/typesafe/maven-releases"
            }
        }

        dependencies{
            playAppCompile "com.typesafe.play:play_2.10:2.3.5"
            playAppRuntime "com.typesafe.play:play-docs_2.10:2.3.5"
        }

        model {
            tasks.runMyAppBinary {
                httpPort = $httpPort
            }
        }
"""
    }

    def "can execute play run task"(){
        setup:
        resources.maybeCopy("PlayRunIntegrationTest/playNew")

        when:
        GradleHandle gradleHandle = executer.withTasks(":runMyAppBinary").start()

        then:
        UrlValidator.available("http://localhost:$httpPort", "Sample Play App", 120000)
        assert new URL("http://localhost:$httpPort").text.contains("Your new application is ready.")

        when: "stopping gradle"
        gradleHandle.abort()
        then: "play server is stopped too"
        notAvailable("http://localhost:$httpPort");
    }

    def notAvailable(String url) {
        try{
            new URL(url).text
            Assert.fail()
        }catch(ConnectException ex){
            return true
        }
        return false
    }
}
