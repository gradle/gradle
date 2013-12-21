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

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.model.GradleProject

@ToolingApiVersion('>=1.0-milestone-5')
@TargetGradleVersion('>=1.0-milestone-5')
class ToolingApiReceivingStandardStreamsCrossVersionSpec extends ToolingApiSpecification {

    def setup() {
        //because embedded tooling api should not replace system out / err
        //we will run below tests only for forked mode
        toolingApi.isEmbedded = false
    }

    def "receives standard streams while the build is executing"() {
        file('build.gradle') << '''
System.out.println 'this is stdout'
System.err.println 'this is stderr'
'''
        def stdout = new ByteArrayOutputStream()
        def stderr = new ByteArrayOutputStream()

        when:
        withConnection { connection ->
            def build = connection.newBuild()
            build.standardOutput = stdout
            build.standardError = stderr
            build.run()
        }

        then:
        stdout.toString().contains('this is stdout')
        stderr.toString().contains('this is stderr')
    }

    def "receives standard streams while the model is building"() {
        file('build.gradle') << '''
System.out.println 'this is stdout'
System.err.println 'this is stderr'
'''

        def stdout = new ByteArrayOutputStream()
        def stderr = new ByteArrayOutputStream()

        when:
        withConnection { connection ->
            def model = connection.model(GradleProject.class)
            model.standardOutput = stdout
            model.standardError = stderr
            return model.get()
        }

        then:
        stdout.toString().contains('this is stdout')
        stderr.toString().contains('this is stderr')
    }
}
