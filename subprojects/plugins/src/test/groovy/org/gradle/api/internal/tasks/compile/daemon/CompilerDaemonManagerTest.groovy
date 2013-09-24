package org.gradle.api.internal.tasks.compile.daemon

import org.gradle.api.internal.project.ProjectInternal
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.util.ConcurrentSpecification
import org.gradle.util.TestUtil

import java.util.concurrent.CountDownLatch

import static org.gradle.test.fixtures.ConcurrentTestUtil.poll

class CompilerDaemonManagerTest extends ConcurrentSpecification {

    def project = TestUtil.createRootProject()

    def "creates new daemon when fork options incompatible"() {
        def forkOptions = Stub(DaemonForkOptions) { isCompatibleWith(_) >> false }
        def m = new CompilerDaemonManager(new DummyCompilerDaemonStarter())

        when:
        Set daemons = []
        daemons << m.getDaemon(project, forkOptions)
        daemons << m.getDaemon(project, forkOptions)

        then:
        daemons.size() == 2
    }

    def "reuses daemons"() {
        def forkOptions = Stub(DaemonForkOptions) { isCompatibleWith(_) >> true }
        def m = new CompilerDaemonManager(new DummyCompilerDaemonStarter())

        when:
        Set daemons = []
        daemons << m.getDaemon(project, forkOptions).setIdle(true)
        daemons << m.getDaemon(project, forkOptions)

        then:
        daemons.size() == 1
    }

    def "allows compiler daemon per thread"() {
        def forkOptions = Stub(DaemonForkOptions) { isCompatibleWith(_) >> true }
        def starter = new DummyCompilerDaemonStarter().blockCreation()
        def m = new CompilerDaemonManager(starter)

        when: //2 threads asking for daemons
        Set daemons = []
        start { daemons << m.getDaemon(project, forkOptions) }
        start { daemons << m.getDaemon(project, forkOptions) }

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
        Set daemons = []
        start { daemons << m.getDaemon(project, forkOptions) }
        start { daemons << m.getDaemon(project, forkOptions) }

        then: //both threads are creating an instance of a daemon at the same time
        poll { assert daemons.size() == 2 }

        when: //daemons finished their jobs
        daemons.each { it.idle = true }

        and: //and new daemon requested
        daemons << m.getDaemon(project, forkOptions)

        then: //one of the daemons is reused
        poll {
            assert daemons.size() == 2
            assert daemons.count { it.idle } == 1
        }
    }

    private static class DummyCompilerDaemonStarter extends CompilerDaemonStarter {
        def latch = new CountDownLatch(0)
        def awaitingCreation = 0

        CompilerDaemonClient startDaemon(ProjectInternal project, DaemonForkOptions forkOptions) {
            awaitingCreation++
            latch.await()
            new CompilerDaemonClient(forkOptions, null, null)
        }

        DummyCompilerDaemonStarter blockCreation() {
            latch = new CountDownLatch(1)
            this
        }

        void unblockCreation() {
            latch.countDown()
        }
    }
}
