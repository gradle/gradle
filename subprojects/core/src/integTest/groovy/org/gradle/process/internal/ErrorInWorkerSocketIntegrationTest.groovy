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

package org.gradle.process.internal


import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.timeout.IntegrationTestTimeout
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import spock.lang.Issue

@Issue("https://github.com/gradle/gradle-private/issues/3247")
@Requires(UnitTestPreconditions.NotJava8OnMacOs)
@IntegrationTestTimeout(180)
class ErrorInWorkerSocketIntegrationTest extends AbstractIntegrationSpec {
    private static final String MESSAGE = 'This breaks socket connection threads in worker process deliberately'

    def "worker won't hang when error occurs in socket connection"() {
        given:
        requireOwnGradleUserHomeDir()

        file('buildSrc/src/main/java/Param.java') << """
import java.io.*;
public class Param implements Serializable {
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        throw new IllegalStateException("$MESSAGE");
    }
}
"""

        file('buildSrc/src/main/java/TestWorkerProcessImpl.java') << '''
import org.gradle.process.internal.worker.RequestHandler;

public class TestWorkerProcessImpl implements RequestHandler<Object, Object> {
    public Object run(Object param) { return null; }
}
'''
        buildFile << '''
import org.gradle.process.internal.worker.WorkerProcessFactory

task runBrokenWorker {
    def rootDir = project.rootDir
    doLast {
        WorkerProcessFactory workerProcessFactory = services.get(WorkerProcessFactory)
        def builder = workerProcessFactory.multiRequestWorker(TestWorkerProcessImpl)
        builder.getJavaCommand().setWorkingDir(rootDir)

        def workerDaemonProcess = builder.build()
        workerDaemonProcess.start()
        workerDaemonProcess.run(new Param())
    }
}
'''
        when:
        fails('runBrokenWorker')

        then:
        failureCauseContains('No response was received from Gradle Worker but the worker process has finished')
        executer.getGradleUserHomeDir().file('workers').listFiles().find { it.name.startsWith('worker-error') }.text.contains(MESSAGE)
    }

    def "worker won't hang when error occurs in socket connection in included build"() {
        given:
        requireOwnGradleUserHomeDir()

        file('included/src/main/java/Param.java') << """
import java.io.*;
public class Param implements Serializable {
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        throw new IllegalStateException("$MESSAGE");
    }
}
"""

        file('included/src/main/java/TestWorkerProcessImpl.java') << '''
import org.gradle.process.internal.worker.RequestHandler;

public class TestWorkerProcessImpl implements RequestHandler<Object, Object> {
    public Object run(Object param) { return null; }
}
'''
        file('included/src/main/groovy/buildlogic.foo.gradle') << '''
import org.gradle.process.internal.worker.WorkerProcessFactory

task runBrokenWorker {
    def rootDir = project.rootDir
    doLast {
        WorkerProcessFactory workerProcessFactory = services.get(WorkerProcessFactory)
        def builder = workerProcessFactory.multiRequestWorker(TestWorkerProcessImpl)
        builder.getJavaCommand().setWorkingDir(rootDir)

        def workerDaemonProcess = builder.build()
        workerDaemonProcess.start()
        workerDaemonProcess.run(new Param())
    }
}
'''
        file('included/build.gradle') << """
plugins {
    id("groovy-gradle-plugin")
}
group = "buildlogic"

"""
        buildFile << '''
plugins {
    id('buildlogic.foo')
}
'''
        settingsFile << '''
            pluginManagement {
                includeBuild('included')
            }
        '''
        when:
        fails('runBrokenWorker')

        then:
        failureCauseContains('No response was received from Gradle Worker but the worker process has finished')
        executer.getGradleUserHomeDir().file('workers').listFiles().find { it.name.startsWith('worker-error') }.text.contains(MESSAGE)
    }
}
