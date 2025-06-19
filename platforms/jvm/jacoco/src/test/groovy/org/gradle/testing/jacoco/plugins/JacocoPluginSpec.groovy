/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.testing.jacoco.plugins

import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.testing.Test
import org.gradle.integtests.fixtures.RepoScriptBlockUtil
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import spock.lang.Issue

class JacocoPluginSpec extends AbstractProjectBuilderSpec {
    def setup() {
        project.apply plugin: 'jacoco'
    }

    def 'jacoco applied to specific JavaExec task'() {
        given:
        JavaExec task = project.tasks.create('exec', JavaExec)
        when:
        project.jacoco.applyTo(task)
        then:
        task.extensions.getByType(JacocoTaskExtension) != null
    }

    def 'jacoco applied to Test task'() {
        given:
        Test task = project.tasks.create('customTest', Test)
        expect:
        task.extensions.getByType(JacocoTaskExtension) != null
    }

    @Requires(UnitTestPreconditions.Online)
    @Issue("GRADLE-3498")
    def 'jacoco task extension can be configured. includeNoLocationClasses: #includeNoLocationClassesValue'() {
        given:
        project.apply plugin: 'java'
        RepoScriptBlockUtil.configureMavenCentral(project.repositories)
        def testTask = project.tasks.getByName('test')
        JacocoTaskExtension extension = testTask.extensions.getByType(JacocoTaskExtension)

        when:
        extension.with {
            destinationFile = project.file('build/jacoco/fake.exec')
            includes = ['org.*', '*.?acoco*']
            excludes = ['org.?joberstar']
            excludeClassLoaders = ['com.sun.*', 'org.fak?.*']
            includeNoLocationClasses = includeNoLocationClassesValue
            sessionId = 'testSession'
            dumpOnExit = false
            output = JacocoTaskExtension.Output.TCP_SERVER
            address = '1.1.1.1'
            port = 100
            classDumpDir = project.file('build/jacoco-dump')
            jmx = true
        }

        def expected = new StringBuilder().with { builder ->
            builder << "destfile=build/jacoco/fake.exec,"
            builder << "append=true,"
            builder << "includes=org.*:*.?acoco*,"
            builder << "excludes=org.?joberstar,"
            builder << "exclclassloader=com.sun.*:org.fak?.*,"
            builder << "inclnolocationclasses=$includeNoLocationClassesValue,"
            builder << "sessionid=testSession,"
            builder << "dumponexit=false,"
            builder << "output=tcpserver,"
            builder << "address=1.1.1.1,"
            builder << "port=100,"
            builder << "classdumpdir=build/jacoco-dump,"
            builder << "jmx=true"
            builder.toString()
        }

        then:
        def jvmArg = extension.asJvmArg.get()
        jvmArg.replaceFirst(/-javaagent:[^=]*\.jar=/, '') == expected

        where:
        includeNoLocationClassesValue << [true, false]
    }

    def "declares task property values for group and description"() {
        given:
        project.apply plugin: 'java'

        expect:
        def jacocoTestReportTask = project.tasks.getByName('jacocoTestReport')
        def jacocoTestCoverageVerificationTask = project.tasks.getByName('jacocoTestCoverageVerification')
        jacocoTestReportTask.group == LifecycleBasePlugin.VERIFICATION_GROUP
        jacocoTestCoverageVerificationTask.group == LifecycleBasePlugin.VERIFICATION_GROUP
        jacocoTestReportTask.description == 'Generates code coverage report for the test task.'
        jacocoTestCoverageVerificationTask.description == 'Verifies code coverage metrics based on specified rules for the test task.'
    }

    def "declares task property values for sourceEncoding without default value"() {
        given:
        project.apply plugin: 'java'

        expect:
        def jacocoTestReportTask = project.tasks.getByName('jacocoTestReport')
        def jacocoTestCoverageVerificationTask = project.tasks.getByName('jacocoTestCoverageVerification')
        !jacocoTestReportTask.sourceEncoding.isPresent()
        !jacocoTestCoverageVerificationTask.sourceEncoding.isPresent()
    }
}
