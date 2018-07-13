/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.integtests.fixtures.RepoScriptBlockUtil
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Issue
import spock.lang.Unroll

// This is not a "real" integration test. It's placed here just to leverage integration test fixture, e.g. jcenter repository mirror.
class JacocoTaskExtensionIntegrationSpec extends AbstractProjectBuilderSpec {
    @Requires(TestPrecondition.ONLINE)
    @Unroll
    @Issue("GRADLE-3498")
    def 'jacoco task extension can be configured. includeNoLocationClasses: #includeNoLocationClassesValue'() {
        given:
        project.apply plugin: 'java'
        project.apply plugin: 'jacoco'
        RepoScriptBlockUtil.configureJcenter(project.repositories)
        def testTask = project.tasks.getByName('test')
        JacocoTaskExtension extension = testTask.extensions.getByType(JacocoTaskExtension)

        when:
        extension.with {
            destinationFile = project.file('build/jacoco/fake.exec')
            append = false
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
            builder << "append=false,"
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
        def jvmArg = extension.asJvmArg
        jvmArg.replaceFirst(/-javaagent:[^=]*\.jar=/, '') == expected

        where:
        includeNoLocationClassesValue << [true, false]
    }
}
