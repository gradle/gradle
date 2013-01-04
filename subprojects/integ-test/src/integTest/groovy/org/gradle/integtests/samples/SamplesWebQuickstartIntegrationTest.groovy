/*
 * Copyright 2007-2008 the original author or authors.
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

package org.gradle.integtests.samples

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.Sample
import org.gradle.test.fixtures.file.TestFile
import org.junit.Rule
import spock.lang.Timeout
import spock.lang.Unroll

/**
 * @author Hans Dockter
 */
class SamplesWebQuickstartIntegrationTest extends AbstractIntegrationSpec {
    @Rule public final Sample sample = new Sample('webApplication/quickstart')

    def "can build a war"() {
        given:
        sample sample

        when:
        run 'clean', 'build'

        then:
        // Check contents of War
        TestFile warContents = file('war-tmp')
        sample.dir.file("build/libs/quickstart.war").unzipTo(warContents)
        warContents.assertHasDescendants(
                'META-INF/MANIFEST.MF',
                'index.jsp',
                'WEB-INF/classes/org/gradle/sample/Greeter.class',
                'WEB-INF/classes/greeting.txt',
                'WEB-INF/lib/log4j-1.2.15.jar',
                'WEB-INF/lib/commons-io-1.4.jar',
        )
    }

    @Timeout(120)
    @Unroll
    def "can use #jettyTask for testing"() {
        expect:
        jettyLifecycle(jettyTask)

        where:
        jettyTask << ["jettyRun", "jettyRunWar"]
    }

    private void jettyLifecycle(String jettyStartTask) {
        def portFinder = org.gradle.util.AvailablePortFinder.createPrivate()
        def httpPort = portFinder.nextAvailable
        def stopPort = portFinder.nextAvailable

        // Inject some int test stuff
        sample.dir.file('build.gradle') << """
httpPort = ${httpPort}
stopPort = ${stopPort}

task runTest << {
    URL url = new URL("http://localhost:\$httpPort/quickstart")
    println url.text
}

task sayHearthyGoodbye << {
    //this task should last for a few seconds
    //to neatly expose issues with jetty killing the main process
    println "About to say goodbye..."
    Thread.sleep(2000)
    println "Jetty will miss you!"
}
"""

        //starting jetty
        sample sample
        def runJetty = executer.withTasks(jettyStartTask, "sayHearthyGoodbye").withArguments("-d").start()

        //jetty is started
        available("http://localhost:$httpPort/quickstart")

        //running web test then stopping jetty
        sample sample
        def jettyStop = executer.withTasks('runTest', 'jettyStop').run()

        //test has completed
        assert jettyStop.output.contains('hello Gradle')

        //jetty completed gracefully
        runJetty.waitForFinish()
        assert runJetty.standardOutput.contains("Jetty will miss you!")
    }

    void available(String theUrl) {
        URL url = new URL(theUrl)
        long expiry = System.currentTimeMillis() + 30000
        while (System.currentTimeMillis() <= expiry) {
            try {
                url.text
                return
            } catch (IOException e) {
                // continue
            }
            Thread.sleep(200)
        }
        throw new RuntimeException("Timeout waiting for jetty to become available.")
    }
}
