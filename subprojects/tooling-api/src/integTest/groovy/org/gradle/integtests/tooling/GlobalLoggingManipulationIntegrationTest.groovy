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
package org.gradle.integtests.tooling

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.tooling.fixture.ToolingApi
import org.gradle.tooling.model.GradleProject
import org.gradle.util.RedirectStdIn
import org.junit.Rule

import java.util.logging.LogManager

class GlobalLoggingManipulationIntegrationTest extends AbstractIntegrationSpec {
    @Rule RedirectStdIn stdIn
    final ToolingApi toolingApi = new ToolingApi(distribution, temporaryFolder)

    def "tooling api does not replace standard streams"() {
        //(SF) only checking if the instances of out and err were not replaced
        //it would be nice to have more meaningful test that verifies the effects of replacing the streams
        given:
        def outInstance = System.out
        def errInstance = System.err

        buildFile << "task hey"

        when:
        GradleProject model = toolingApi.withConnection { connection -> connection.getModel(GradleProject.class) }

        then:
        model.tasks.find { it.name == 'hey' }
        System.out.is(outInstance)
        System.err.is(errInstance)
    }

    static class FailingInputStream extends InputStream implements GroovyInterceptable {

        int read() {
            throw new RuntimeException("Input stream should not be consumed");
        }

        def invokeMethod(String name, args) {
            throw new RuntimeException("Input stream should not be consumed");
        }
    }

    def "tooling api should never consume the std in"() {
        given:
        System.in = new FailingInputStream()
        buildFile << "task hey"

        when:
        toolingApi.withConnection { connection -> connection.newBuild().run() }

        then:
        noExceptionThrown()
    }

    def "tooling api does not reset the java logging"() {
        //(SF) checking if the logger level was not overridden.
        //this gives some confidence that the LogManager was not reset
        given:
        LogManager.getLogManager().getLogger("").setLevel(java.util.logging.Level.OFF);
        buildFile << "task hey"

        when:
        assert java.util.logging.Level.OFF == LogManager.getLogManager().getLogger("").level
        GradleProject model = toolingApi.withConnection { connection -> connection.getModel(GradleProject.class) }

        then:
        model.tasks.find { it.name == 'hey' }
        java.util.logging.Level.OFF == LogManager.getLogManager().getLogger("").level
    }
}
