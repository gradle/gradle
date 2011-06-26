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
package org.gradle.integtests.tooling.m3

import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.BuildException
import org.gradle.tooling.ProgressListener
import org.gradle.tooling.model.Project

class ToolingApiModelIntegrationTest extends ToolingApiSpecification {
    def "receives progress and logging while the model is building"() {
        dist.testFile('build.gradle') << '''
System.out.println 'this is stdout'
System.err.println 'this is stderr'
'''

        def stdout = new ByteArrayOutputStream()
        def stderr = new ByteArrayOutputStream()
        def progressMessages = []

        when:
        withConnection { connection ->
            def model = connection.model(Project.class)
            model.standardOutput = stdout
            model.standardError = stderr
            model.addProgressListener({ event -> progressMessages << event.description } as ProgressListener)
            return model.get()
        }

        then:
        stdout.toString().contains('this is stdout')
        stderr.toString().contains('this is stderr')
        progressMessages.size() >= 2
        progressMessages.pop() == ''
        progressMessages.every { it }
    }

    def "tooling api reports failure to build model"() {
        dist.testFile('build.gradle') << 'broken'

        when:
        withConnection { connection ->
            return connection.getModel(Project.class)
        }

        then:
        BuildException e = thrown()
        e.message.startsWith('Could not fetch model of type \'Project\' using Gradle')
        e.cause.message.contains('A problem occurred evaluating root project')
    }
}
