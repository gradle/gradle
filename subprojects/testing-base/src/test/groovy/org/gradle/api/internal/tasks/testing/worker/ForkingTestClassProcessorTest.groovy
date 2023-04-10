/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.worker

import com.google.common.collect.ImmutableList
import org.gradle.api.Action
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.tasks.testing.TestClassRunInfo
import org.gradle.api.internal.tasks.testing.TestClassStealer
import org.gradle.api.internal.tasks.testing.WorkerTestClassProcessorFactory
import org.gradle.internal.exceptions.DefaultMultiCauseException
import org.gradle.internal.remote.ObjectConnection
import org.gradle.internal.work.WorkerThreadRegistry
import org.gradle.process.JavaForkOptions
import org.gradle.process.internal.ExecException
import org.gradle.process.internal.JavaExecHandleBuilder
import org.gradle.process.internal.worker.WorkerProcess
import org.gradle.process.internal.worker.WorkerProcessBuilder
import org.gradle.process.internal.worker.WorkerProcessContext
import org.gradle.process.internal.worker.WorkerProcessFactory
import spock.lang.Specification

class ForkingTestClassProcessorTest extends Specification {
    WorkerThreadRegistry workerLeaseRegistry = Mock(WorkerThreadRegistry)
    RemoteTestClassProcessor remoteProcessor = Mock(RemoteTestClassProcessor)
    ObjectConnection connection = Mock(ObjectConnection) {
        addOutgoing(RemoteTestClassProcessor.class) >> remoteProcessor
    }
    WorkerProcess workerProcess = Mock(WorkerProcess) {
        getConnection() >> this.connection
    }
    WorkerProcessBuilder workerProcessBuilder = Mock(WorkerProcessBuilder) {
        build() >> workerProcess
        getJavaCommand() >> Stub(JavaExecHandleBuilder)
    }
    TestClassStealer stealer = Mock(TestClassStealer)
    WorkerProcessFactory workerProcessFactory = Stub(WorkerProcessFactory) {
        create(_ as Action<WorkerProcessContext>) >> workerProcessBuilder
    }

    def "acquires worker lease and starts worker process on first test"() {
        given:
        def test1 = Mock(TestClassRunInfo)
        def test2 = Mock(TestClassRunInfo)
        def processor = newProcessor()

        when:
        processor.processTestClass(test1)
        processor.processTestClass(test2)

        then:
        1 * workerLeaseRegistry.startWorker()
        1 * remoteProcessor.processTestClass(test1)
        1 * stealer.add(test1, processor)
        1 * remoteProcessor.processTestClass(test2)
        1 * stealer.add(test2, processor)
        1 * remoteProcessor.startProcessing()
        0 * remoteProcessor._
    }

    def "starts process with the specified classpath"() {
        given:
        def appClasspath = ImmutableList.of(new File("cls.jar"))
        def appModulepath = ImmutableList.of(new File("mod.jar"))
        def implClasspath = ImmutableList.of(new URL("file://cls.jar"))
        def implModulepath = ImmutableList.of(new URL("file://mod.jar"))
        def processor = newProcessor(new ForkedTestClasspath(
            appClasspath, appModulepath, implClasspath, implModulepath
        ))

        when:
        processor.forkProcess()

        then:
        1 * workerProcessBuilder.applicationClasspath(_) >> { List it -> assert it[0] == appClasspath }
        1 * workerProcessBuilder.applicationModulePath(_) >> { List it ->  assert it[0] == appModulepath}
        1 * workerProcessBuilder.setImplementationClasspath(_) >> { List it -> assert it[0] == implClasspath }
        1 * workerProcessBuilder.setImplementationModulePath(_) >> { List it -> assert it[0] == implModulepath }
    }

    def "stopNow does nothing when no remote processor"() {
        given:
        def processor = newProcessor()

        when:
        processor.stopNow()

        then:
        0 * _
    }

    def "stopNow propagates to worker process"() {
        given:
        def processor = newProcessor()

        when:
        processor.processTestClass(Mock(TestClassRunInfo))
        processor.stopNow()

        then:
        1 * workerProcess.stopNow()
    }

    def "no exception when stop after stopNow"() {
        def processor = newProcessor()

        when:
        processor.processTestClass(Mock(TestClassRunInfo))
        processor.stopNow()
        processor.stop()

        then:
        1 * workerProcess.stopNow()
        _ * workerProcess.waitForStop() >> { throw new ExecException("waitForStop can throw") }
        notThrown(ExecException)
    }

    def "captures and rethrows unrecoverable exceptions thrown by the connection"() {
        def handler = null
        def processor = newProcessor()

        when:
        processor.processTestClass(Mock(TestClassRunInfo))

        then:
        1 * workerProcess.getConnection() >> Stub(ObjectConnection) {
            addUnrecoverableErrorHandler(_ as Action<Throwable>) >> { List args -> handler = args[0] }
            addOutgoing(_ as Class<RemoteTestClassProcessor>) >> Stub(RemoteTestClassProcessor)
        }

        when:
        def unexpectedException = new Throwable('BOOM!')
        handler.execute(unexpectedException)

        and:
        processor.stop()

        then:
        def e = thrown(DefaultMultiCauseException)
        e.causes.contains(unexpectedException)
    }

    def "ignores unrecoverable exceptions after stopNow() is called"() {
        def handler
        def processor = newProcessor()

        when:
        processor.processTestClass(Mock(TestClassRunInfo))

        then:
        1 * workerProcess.getConnection() >> Stub(ObjectConnection) {
            addUnrecoverableErrorHandler(_ as Action<Throwable>) >> { List args -> handler = args[0] }
            addOutgoing(_ as Class<RemoteTestClassProcessor>) >> Stub(RemoteTestClassProcessor)
        }

        when:
        processor.stopNow()
        def unexpectedException = new Throwable('BOOM!')
        handler !=null
        handler.execute(unexpectedException)

        and:
        processor.stop()

        then:
        noExceptionThrown()
    }

    def newProcessor(
        ForkedTestClasspath classpath = new ForkedTestClasspath(ImmutableList.of(), ImmutableList.of(), ImmutableList.of(), ImmutableList.of())
    ) {
        return new ForkingTestClassProcessor(
            workerLeaseRegistry, workerProcessFactory, Mock(WorkerTestClassProcessorFactory),
            Stub(JavaForkOptions), classpath, Mock(Action), Mock(DocumentationRegistry), stealer
        )
    }
}
