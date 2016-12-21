/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.process.internal.daemon

import org.gradle.api.internal.file.FileResolver
import org.gradle.util.UsesNativeServices
import spock.lang.Specification
import org.gradle.internal.Factory

import java.util.concurrent.atomic.AtomicBoolean

@UsesNativeServices
class WorkerDaemonRunnableExecutorTest extends Specification {
    def workerDaemonFactory = Mock(WorkerDaemonFactory)
    def workerDaemon = Mock(WorkerDaemon)
    def fileResolver = Mock(FileResolver)
    def factory = Mock(Factory)
    def serverImpl = Mock(WorkerDaemonProtocol)
    def workerDaemonRunnableExecutor

    def setup() {
        _ * fileResolver.resolveLater(_) >> factory
        1 * factory.create()
        workerDaemonRunnableExecutor = new WorkerDaemonRunnableExecutor(workerDaemonFactory, fileResolver, TestRunnable.class, serverImpl.class)
    }

    def "executor executes the given runnable in a daemon"() {
        given:
        AtomicBoolean executed = new AtomicBoolean(false)

        when:
        workerDaemonRunnableExecutor.params(executed).execute()

        then:
        1 * workerDaemonFactory.getDaemon(_, _, _) >> workerDaemon
        1 * workerDaemon.execute(_, _) >> { action, spec ->
            action.execute(spec)
            return new WorkerDaemonResult(true, null)
        }

        and:
        executed.get()
    }

    public static class TestRunnable implements Runnable {
        private final AtomicBoolean executed

        TestRunnable(AtomicBoolean executed) {
            this.executed = executed
        }

        @Override
        void run() {
            println "executing"
            executed.set(true)
        }
    }
}
