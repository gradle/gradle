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
import org.junit.Rule
import spock.lang.Ignore
import spock.lang.Timeout

class PlayRunIntegrationTest extends AbstractIntegrationSpec {
    @Rule
    public final TestResources resources = new TestResources(temporaryFolder)

    def setup(){
        int httpPort = 9000
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

        }

        model {
            tasks.runMyAppBinary {
                String content;
                doFirst{
                    Thread.start{
                        available("http://localhost:$httpPort")
                        content = new URL("http://localhost:$httpPort").text
                        stop()
                    }
                }
                doLast{
                    assert content.contains("Your new application is ready.")
                }
            }
        }

        def stop() {
          URL url = new URL("http://localhost:$httpPort/stop")
          try {
            url.text
          } catch (IOException e) {
            //pass
          }
        }

        def available(String theUrl, int timeout = 30000) {
            URL url = new URL(theUrl)
            long expiry = System.currentTimeMillis() + timeout
            while (System.currentTimeMillis() <= expiry) {
                try {
                    url.text
                    return
                } catch (IOException e) {
                    // continue
                }
                Thread.sleep(200)
            }
            throw new RuntimeException("Timeout waiting for \$theUrl to become available.");
        }
"""
    }

    @Timeout(60)
    @Ignore
    def "can execute play run task"(){
        resources.maybeCopy("PlayRunIntegrationTest/playNew")
        when:
        succeeds("runMyApp")
        then:
        executed(":routesCompileMyAppBinary", ":twirlCompileMyAppBinary", ":scalaCompileMyAppBinary", ":createMyAppBinaryJar", ":myAppBinary", ":runMyAppBinary")
    }
}
