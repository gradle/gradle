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

package org.gradle.play.integtest
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.Sample
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.integtests.fixtures.executer.GradleHandle
import org.gradle.util.AvailablePortFinder
import org.junit.Rule
import spock.lang.IgnoreIf

import static org.gradle.integtests.fixtures.UrlValidator.*

class PlaySampleIntegrationTest extends AbstractIntegrationSpec {
    def portFinder = AvailablePortFinder.createPrivate()
    def initScript
    int httpPort

    @Rule
    Sample basicPlaySample = new Sample(temporaryFolder, "play/basic")

    @Rule
    Sample advancedPlaySample = new Sample(temporaryFolder, "play/advanced")

    def setup() {
        httpPort = portFinder.nextAvailable
        initScript = file("initFile") << """
            gradle.allprojects {
                model {
                    tasks.runPlayBinary {
                        httpPort = $httpPort
                    }
                }
            }
        """
    }

    def "can build play sample"() {
        given:
        sample basicPlaySample
        expect:
        succeeds "assemble"
    }

    /**
     * Don't currently run with DaemonExecuter, because
     * InputForwarder is consuming stdin eagerly.
     * */
    @IgnoreIf({ GradleContextualExecuter.isDaemon() })
    def "produces usable application from basic sample"() {
        when:
        executer.usingInitScript(initScript)
        sample basicPlaySample

        // Assemble first so that build time doesn't play into the startup timeout
        then:
        succeeds "assemble"

        when:
        PipedInputStream inputStream = new PipedInputStream();
        PipedOutputStream stdinWriter = new PipedOutputStream(inputStream);
        executer.withStdIn(inputStream)
        executer.usingInitScript(initScript)
        sample basicPlaySample
        GradleHandle gradleHandle = executer.withTasks(":runPlayBinary").start()

        then:
        available("http://localhost:$httpPort", "Play app", 60000)
        assert playUrl().text.contains("Your new application is ready.")

        and:
        assertUrlContent playUrl("assets/stylesheets/main.css"), publicAsset(basicPlaySample, "stylesheets/main.css")
        assertUrlContent playUrl("assets/javascripts/hello.js"), publicAsset(basicPlaySample, "javascripts/hello.js")
        assertUrlContent playUrl("assets/images/favicon.png"), publicAsset(basicPlaySample, "images/favicon.png")

        when: "stopping gradle"
        stdinWriter.write(4) // ctrl+d
        stdinWriter.flush()
        gradleHandle.waitForFinish()

        then: "play server is stopped too"
        notAvailable("http://localhost:$httpPort")
    }

    @IgnoreIf({ GradleContextualExecuter.isDaemon() })
    def "produces usable application from advanced sample"() {
        when:
        executer.usingInitScript(initScript)
        sample advancedPlaySample

        // Assemble first so that build time doesn't play into the startup timeout
        then:
        succeeds "assemble"

        when:
        PipedInputStream inputStream = new PipedInputStream();
        PipedOutputStream stdinWriter = new PipedOutputStream(inputStream);
        executer.withStdIn(inputStream)
        executer.usingInitScript(initScript)
        sample advancedPlaySample
        GradleHandle gradleHandle = executer.withTasks(":runPlayBinary").start()

        then:
        available("http://localhost:$httpPort", "Play app", 60000)
        assert playUrl().text.contains("Your new application is ready.")

        and:
        assertUrlContent playUrl("assets/stylesheets/main.css"), publicAsset(advancedPlaySample, "stylesheets/main.css")
        assertUrlContent playUrl("assets/javascripts/hello.js"), publicAsset(advancedPlaySample, "javascripts/hello.js")
        assertUrlContent playUrl("assets/images/favicon.png"), publicAsset(advancedPlaySample, "images/favicon.png")
        assertUrlContent playUrl("assets/javascripts/sample.js"), appAsset(advancedPlaySample, "javascripts/sample.js")
        assertUrlContent playUrl("assets/coffeescript/console.js"), coffeeScriptGeneratedJavaScript
        assertUrlContent playUrl("hello/Gradle"), "Hello Gradle!"

        when: "stopping gradle"
        stdinWriter.write(4) // ctrl+d
        stdinWriter.flush()
        gradleHandle.waitForFinish()

        then: "play server is stopped too"
        notAvailable("http://localhost:$httpPort")
    }

    URL playUrl(String path='') {
        return new URL("http://localhost:$httpPort/${path}")
    }

    File publicAsset(Sample sample, String asset) {
        return new File(sample.dir, "public/${asset}")
    }

    File appAsset(Sample sample, String asset) {
        return new File(sample.dir, "app/assets/${asset}")
    }

    String getCoffeeScriptGeneratedJavaScript() {
        return """(function() {
  var square;

  console.log("This is coffeescript!");

  square = function(x) {
    return x * x;
  };

}).call(this);
"""
    }
}