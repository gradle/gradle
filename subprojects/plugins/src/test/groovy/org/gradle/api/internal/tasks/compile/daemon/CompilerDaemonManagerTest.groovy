/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.daemon

import org.gradle.util.ConcurrentSpecification

import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.locks.ReentrantLock

import static org.gradle.test.fixtures.ConcurrentTestUtil.poll

class CompilerDaemonManagerTest extends ConcurrentSpecification {

    def workingDir = new File("some-dir")

    def "creates new daemon when fork options incompatible"() {
        def forkOptions = Stub(DaemonForkOptions) { isCompatibleWith(_) >> false }
        def m = new CompilerDaemonManager(new DummyCompilerDaemonStarter())

        when:
        Set daemons = []
        daemons << m.getDaemon(workingDir, forkOptions)
        daemons << m.getDaemon(workingDir, forkOptions)

        then:
        daemons.size() == 2
    }

    def "reuses daemons"() {
        def forkOptions = Stub(DaemonForkOptions) { isCompatibleWith(_) >> true }
        def m = new CompilerDaemonManager(new DummyCompilerDaemonStarter())

        when:
        Set daemons = []
        daemons << m.getDaemon(workingDir, forkOptions).setIdle(true)
        daemons << m.getDaemon(workingDir, forkOptions)

        then:
        daemons.size() == 1
    }

    def "allows compiler daemon per thread"() {
        def forkOptions = Stub(DaemonForkOptions) { isCompatibleWith(_) >> true }
        def starter = new DummyCompilerDaemonStarter().blockCreation()
        def m = new CompilerDaemonManager(starter)

        when: //2 threads asking for daemons
        Set daemons = new CopyOnWriteArraySet()
        start { daemons << m.getDaemon(workingDir, forkOptions) }
        start { daemons << m.getDaemon(workingDir, forkOptions) }

        then: //both threads are creating an instance of a daemon at the same time
        poll { assert starter.awaitingCreation == 2 }

        when: //complete creation of the daemons
        starter.unblockCreation()

        then:
        poll { assert daemons.size() == 2 }
    }

    def "reuses daemons in concurrent scenario"() {
        def forkOptions = Stub(DaemonForkOptions) { isCompatibleWith(_) >> true }
        def m = new CompilerDaemonManager(new DummyCompilerDaemonStarter())

        when: //2 threads asking for daemons
        Set daemons = new CopyOnWriteArraySet()
        start { daemons << m.getDaemon(workingDir, forkOptions) }
        start { daemons << m.getDaemon(workingDir, forkOptions) }

        then: //both threads are creating an instance of a daemon at the same time
        poll { assert daemons.size() == 2 }

        when: //daemons finished their jobs
        daemons.each { it.idle = true }

        and: //and new daemon requested
        daemons << m.getDaemon(workingDir, forkOptions)

        then: //one of the daemons is reused
        poll {
            assert daemons.size() == 2
            assert daemons.count { it.idle } == 1
        }
    }

    private static class DummyCompilerDaemonStarter extends CompilerDaemonStarter {
        def lock = new ReentrantLock()
        def condition = lock.newCondition()
        def boolean blocked
        def awaitingCreation = 0

        DummyCompilerDaemonStarter() {
            super(null, null)
        }

        CompilerDaemonClient startDaemon(File workingDir, DaemonForkOptions forkOptions) {
            lock.lock()
            try {
                awaitingCreation++
                while (blocked) {
                    condition.await()
                }
            } finally {
                lock.unlock()
            }
            new CompilerDaemonClient(forkOptions, null, null)
        }

        DummyCompilerDaemonStarter blockCreation() {
            lock.lock()
            try {
                blocked = true
            } finally {
                lock.unlock()
            }
            this
        }

        void unblockCreation() {
            lock.lock()
            try {
                blocked = false
                condition.signalAll()
            } finally {
                lock.unlock()
            }
        }
    }
}
