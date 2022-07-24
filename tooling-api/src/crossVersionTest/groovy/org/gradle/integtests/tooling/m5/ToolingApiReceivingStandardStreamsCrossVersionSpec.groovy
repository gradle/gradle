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

import org.gradle.integtests.tooling.fixture.ActionQueriesModelThatRequiresConfigurationPhase
import org.gradle.integtests.tooling.fixture.ToolingApiLoggingSpecification
import org.gradle.test.fixtures.file.TestFile
import org.gradle.tooling.model.GradleProject
import org.gradle.util.GradleVersion

class ToolingApiReceivingStandardStreamsCrossVersionSpec extends ToolingApiLoggingSpecification {

    def "receives standard streams while the build is executing"() {
        given:
        loggingBuildScript()

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
        stdout.toString().contains('A warning using SLF4j')
        stdout.toString().contains('A warning using JCL')
        stdout.toString().contains('A warning using Log4j')
        stdout.toString().contains('A warning using JUL')
        if (targetVersion.baseVersion >= GradleVersion.version('4.7')) {
            // Changed handling of error log messages
            stdout.toString().contains('this is stderr')
        } else {
            stderr.toString().contains('this is stderr')
        }
    }

    def "receives standard streams while the model is building"() {
        given:
        loggingBuildScript()

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
        stdout.toString().contains('A warning using SLF4j')
        stdout.toString().contains('A warning using JCL')
        stdout.toString().contains('A warning using Log4j')
        stdout.toString().contains('A warning using JUL')
        if (targetVersion.baseVersion >= GradleVersion.version('4.7')) {
            // Changed handling of error log messages
            stdout.toString().contains('this is stderr')
        } else {
            stderr.toString().contains('this is stderr')
        }
    }

    def "receives standard streams while client action is running"() {
        given:
        loggingBuildScript()

        def stdout = new ByteArrayOutputStream()
        def stderr = new ByteArrayOutputStream()

        when:
        withConnection { connection ->
            def action = connection.action(new ActionQueriesModelThatRequiresConfigurationPhase())
            action.standardOutput = stdout
            action.standardError = stderr
            return action.run()
        }

        then:
        stdout.toString().contains('this is stdout')
        stdout.toString().contains('A warning using SLF4j')
        stdout.toString().contains('A warning using JCL')
        stdout.toString().contains('A warning using Log4j')
        stdout.toString().contains('A warning using JUL')
        if (targetVersion.baseVersion >= GradleVersion.version('4.7')) {
            // Changed handling of error log messages
            stdout.toString().contains('this is stderr')
        } else {
            stderr.toString().contains('this is stderr')
        }
    }

    private TestFile loggingBuildScript() {
        file('build.gradle') << '''
System.out.println 'this is stdout'
System.err.println 'this is stderr'

def slf4jLogger = org.slf4j.LoggerFactory.getLogger('some-logger')
slf4jLogger.warn('A warning using SLF4j')

def jclLogger = org.apache.commons.logging.LogFactory.getLog('some-logger')
jclLogger.warn('A warning using JCL')

def log4jLogger = org.apache.log4j.Logger.getLogger('some-logger')
log4jLogger.warn('A warning using Log4j')

def julLogger = java.util.logging.Logger.getLogger('some-logger')
julLogger.warning('A warning using JUL')
'''
    }
}
