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

import org.gradle.api.Project
import org.gradle.internal.jacoco.JacocoAgentJar
import org.gradle.process.JavaForkOptions
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification

class JacocoTaskExtensionSpec extends Specification {
    JacocoAgentJar agent = Mock()
    JavaForkOptions task = Mock()
    Project project = ProjectBuilder.builder().build()
    JacocoTaskExtension extension = TestUtil.newInstance(JacocoTaskExtension.class, project.objects, agent, task)
    @Rule final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    def 'asJvmArg with default arguments assembles correct string'() {
        setup:
        agent.supportsJmx() >> true
        agent.supportsInclNoLocationClasses() >> true
        agent.jar >> temporaryFolder.file('fakeagent.jar')
        task.getWorkingDir() >> temporaryFolder.file(".")
        expect:
        extension.asJvmArg == "-javaagent:${agent.jar.absolutePath}=append=true,inclnolocationclasses=false,dumponexit=true,output=file,jmx=false"
    }

    def 'supports jacocoagent with no jmx support'() {
        given:
        agent.supportsJmx() >> false
        agent.jar >> temporaryFolder.file('fakeagent.jar')
        task.getWorkingDir() >> temporaryFolder.file("workingDir")

        expect:
        extension.asJvmArg == "-javaagent:${agent.jar.absolutePath}=append=true,dumponexit=true,output=file"
    }

    def 'supports jacocoagent with no inclNoLocationClasses support'() {
        given:
        agent.supportsInclNoLocationClasses() >> false
        agent.jar >> temporaryFolder.file('fakeagent.jar')
        task.getWorkingDir() >> temporaryFolder.file("workingDir")

        expect:
        extension.asJvmArg == "-javaagent:${agent.jar.absolutePath}=append=true,dumponexit=true,output=file"
    }

    def 'asJvmArg with all arguments assembles correct string. includeNoLocationClasses: #includeNoLocationClassesValue'() {
        given:
        agent.supportsJmx() >> true
        agent.supportsInclNoLocationClasses() >> true
        agent.jar >> temporaryFolder.file('workingDir/subfolder/fakeagent.jar')
        task.getWorkingDir() >> temporaryFolder.file("workingDir")

        extension.with {
            destinationFile = temporaryFolder.file('build/jacoco/fake.exec')
            includes = ['org.*', '*.?acoco*']
            excludes = ['org.?joberstar']
            excludeClassLoaders = ['com.sun.*', 'org.fak?.*']
            includeNoLocationClasses = includeNoLocationClassesValue
            sessionId = 'testSession'
            dumpOnExit = false
            output = JacocoTaskExtension.Output.TCP_SERVER
            address = '1.1.1.1'
            port = 100
            classDumpDir = temporaryFolder.file('build/jacoco-dump')
            jmx = true
        }

        def expected = new StringBuilder().with { builder ->
            builder << "-javaagent:${agent.jar.absolutePath}="
            builder << "destfile=../build/jacoco/fake.exec,"
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
            builder << "classdumpdir=../build/jacoco-dump,"
            builder << "jmx=true"
            builder.toString()
        }
        expect:
        extension.asJvmArg == expected

        where:
        includeNoLocationClassesValue << [true, false]
    }

    def 'asJvmArg fails if agent cannot extract the JAR'() {
        given:
        agent.jar >> { throw new Exception() }
        when:
        extension.asJvmArg
        then:
        thrown Exception
    }

}
