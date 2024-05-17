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
package org.gradle.integtests.tooling.m5


import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.BuildException
import org.gradle.tooling.model.GradleProject

class ToolingApiModelCrossVersionSpec extends ToolingApiSpecification {
    def "receives progress while the model is building"() {
        file('build.gradle') << '''
System.out.println 'this is stdout'
System.err.println 'this is stderr'
'''

        when:
        def progress = withModel(GradleProject.class).progressMessages
        progress.removeLast()

        then:
        progress.size() >= 2
        progress.every { it }
    }

    def "tooling api reports failure to build model"() {
        file('build.gradle') << 'broken'

        when:
        withConnection {
            it.model(GradleProject.class).get()
        }

        then:
        BuildException e = thrown()
        e.message.startsWith('Could not fetch model of type \'GradleProject\' using')
        e.cause.message.contains('A problem occurred evaluating root project')

        and:
        failure.assertHasDescription('A problem occurred evaluating root project')
        assertHasConfigureFailedLogging()
    }
}
