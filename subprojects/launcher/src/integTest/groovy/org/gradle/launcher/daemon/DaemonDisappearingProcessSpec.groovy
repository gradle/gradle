/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.launcher.daemon

import org.gradle.util.TestFile
import org.junit.Rule
import spock.lang.Specification
import org.gradle.integtests.fixtures.*
import org.gradle.process.internal.ExecHandle
import org.gradle.process.internal.ExecHandleBuilder
import spock.lang.*

class DaemonDisappearingProcessSpec extends Specification {

    @Rule public final GradleDistribution distribution = new GradleDistribution()

    def processes = []

    def setup() {
        distribution.requireOwnUserHomeDir()
        distribution.file("build.gradle") << """
            task('sleep') << {
                println "about to sleep"
                sleep 10000
            }
        """
    }

    ForkingGradleExecuter executer() {
        new ForkingGradleExecuter(distribution.gradleHomeDir).
            usingProjectDirectory(distribution.testDir).
            withUserHomeDir(distribution.userHomeDir)
    }

    ExecHandle withStreams(ExecHandleBuilder builder) {
        builder.standardOutput = new ByteArrayOutputStream()
        builder.errorOutput = new ByteArrayOutputStream()
        def process = builder.build()
        processes << process
        process.metaClass.getOutput = { -> builder.standardOutput.toString() }
        process.metaClass.getError = { -> builder.errorOutput.toString() }
        process
    }

    ExecHandle client() {
        withStreams(executer().withArguments("--daemon").withTasks("sleep").createExecHandleBuilder())
    }

    ExecHandle daemon() {
        withStreams(executer().withArguments("--daemon", "--foreground").createExecHandleBuilder())
    }

    @Timeout(10)
    def "tearing down client while daemon is building tears down daemon"() {
        given:
        def client = client()
        def daemon = daemon()

        when:
        daemon.start()

        and:
        client.start()

        then:
        waitFor { assert daemon.output.contains("about to sleep"); true }

        when:
        client.abort()

        then:
        waitFor { daemon.waitForFinish() }.exitValue == 1

        and:
        daemon.output.contains "client disconnection detected"
    }

    def waitFor(int timeout = 10000, Closure assertion) {
        int x = 0;
        while(true) {
            try {
                def value = assertion()
                if (!value) {
                    throw new AssertionError("'$value' is not true")
                }
                return value
            } catch (Throwable t) {
                Thread.sleep(100);
                x += 100;
                if (x > timeout) {
                    throw t;
                }
            }
        }
    }

    def cleanup() {
        try {
            processes*.abort()
        } catch (IllegalStateException e) {}
            
        processes.each { process ->
            waitFor { process.waitForFinish() }
        }
    }

}