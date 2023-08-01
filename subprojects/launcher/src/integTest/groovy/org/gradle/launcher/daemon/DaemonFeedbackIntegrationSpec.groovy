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

package org.gradle.launcher.daemon

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.daemon.DaemonIntegrationSpec
import org.gradle.integtests.fixtures.timeout.IntegrationTestTimeout
import org.gradle.launcher.daemon.logging.DaemonMessages
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.util.GradleVersion
import spock.lang.Issue

import static org.gradle.test.fixtures.ConcurrentTestUtil.poll

@LeaksFileHandles
class DaemonFeedbackIntegrationSpec extends DaemonIntegrationSpec {

    @Override
    def setupBuildOperationFixture() {
        //disable because of a test that is incompatible with the build operation fixture
    }

    def "daemon keeps logging to the file even if the build is stopped"() {
        given:
        file("build.gradle") << """
task sleep {
    doLast {
        println 'taking a nap...'
        Thread.sleep(10000)
        println 'finished the nap...'
    }
}
"""

        when:
        def sleeper = executer.withArguments('-i').withTasks('sleep').start()

        then:
        poll(60) {
            assert readLog(executer.daemonBaseDir).contains("taking a nap...")
        }

        when:
        executer.withArguments("--stop").run()

        then:
        sleeper.waitForFailure()

        poll(5, 1) {
            def log = readLog(executer.daemonBaseDir)
            assert log.contains(DaemonMessages.REMOVING_PRESENCE_DUE_TO_STOP)
            assert log.contains(DaemonMessages.DAEMON_VM_SHUTTING_DOWN)
        }
    }

    @IntegrationTestTimeout(25)
    def "promptly shows decent message when daemon cannot be started"() {
        when:
        executer.withArguments("-Dorg.gradle.jvmargs=-Xyz").run()

        then:
        def ex = thrown(Exception)
        ex.message.contains(DaemonMessages.UNABLE_TO_START_DAEMON)
        ex.message.contains("Process command line:")
        ex.message.contains("-Dorg.gradle.jvmargs=-Xyz")
        ex.message.contains("Unrecognized option: -Xyz")
    }

    @ToBeFixedForConfigurationCache(because = "asserts on configuration phase logging")
    def "daemon log contains all necessary logging"() {
        given:
        file("build.gradle") << "println 'Hello build!'"

        when:
        executer.withArguments("-i").run()

        then:
        def log = readLog(executer.daemonBaseDir)

        //output before started relying logs via connection
        log.count(DaemonMessages.PROCESS_STARTED) == 1
        //output after started relying logs via connection
        log.count(DaemonMessages.STARTED_RELAYING_LOGS) == 1
        //output from the build
        log.count('Hello build!') == 1

        when: "another build requested with the same daemon"
        executer.withArguments("-i").run()

        then:
        def aLog = readLog(executer.daemonBaseDir)

        aLog.count(DaemonMessages.PROCESS_STARTED) == 1
        aLog.count(DaemonMessages.STARTED_RELAYING_LOGS) == 2
        aLog.count('Hello build!') == 2
    }

    def "background daemon infrastructure logs with DEBUG"() {
        given:
        file("build.gradle") << "task foo { doLast { println 'hey!' } }"

        when: "running build with --info"
        executer.withArguments("-i").withTasks('foo').run()

        then:
        def log = readLog(executer.daemonBaseDir)
        log.findAll(DaemonMessages.STARTED_EXECUTING_COMMAND).size() == 1

        poll(60) {
            //in theory the client could have received result and complete
            // but the daemon has not yet finished processing hence polling
            def daemonLog = readLog(executer.daemonBaseDir)
            assert daemonLog.findAll(DaemonMessages.FINISHED_EXECUTING_COMMAND).size() == 1
            assert daemonLog.findAll(DaemonMessages.FINISHED_BUILD).size() == 1
        }

        when: "another build requested with the same daemon with --info"
        executer.withArguments("-i").withTasks('foo').run()

        then:
        def aLog = readLog(executer.daemonBaseDir)
        aLog.findAll(DaemonMessages.STARTED_EXECUTING_COMMAND).size() == 2
    }

    def "daemon log honors log levels for logging"() {
        given:
        file("build.gradle") << """
            println 'println me!'

            logger.debug('debug me!')
            logger.info('info me!')
            logger.quiet('quiet me!')
            logger.lifecycle('lifecycle me!')
            logger.warn('warn me!')
            logger.error('error me!')
        """

        when:
        executer.withArguments("-q").run()

        then:
        def log = readLog(executer.daemonBaseDir)

        //daemon logs to file eagerly regardless of the build log level
        log.count(DaemonMessages.STARTED_RELAYING_LOGS) == 1
        //output from the build:
        log.count('debug me!') == 0
        log.count('info me!') == 0
        log.count('println me!') == 1
        log.count('quiet me!') == 1
        log.count('lifecycle me!') == 0
        log.count('warn me!') == 0
        log.count('error me!') == 1
    }

    @Issue("https://github.com/gradle/gradle/issues/8833")
    @Requires(UnitTestPreconditions.NotWindows)
    def "daemon log restricts permissions to owner"() {
        given:
        file("build.gradle") << """
        """

        when:
        executer.withArguments("-q").run()

        then:
        def log = firstLog(executer.daemonBaseDir)
        assertLogIsOnlyVisibleToOwner(log)
    }

    void assertLogIsOnlyVisibleToOwner(File logFile) {
        assert new TestFile(logFile).permissions == "rw-------"
    }

    //Java 9 and above needs --add-opens to make environment variable mutation work
    @Requires(UnitTestPreconditions.Jdk8OrEarlier)
    @ToBeFixedForConfigurationCache(because = "asserts on configuration phase logging")
    def "foreground daemon log honors log levels for logging"() {
        given:
        file("build.gradle") << """
            logger.debug('debug me!')
            logger.info('info me!')
        """

        when:
        def daemon = executer.noExtraLogging().withArguments("--foreground").start()

        then:
        poll(60) { assert daemon.standardOutput.contains(DaemonMessages.PROCESS_STARTED) }

        when:
        def infoBuild = executer.withArguments("-i").run()

        then:
        infoBuild.output.count("debug me!") == 0
        infoBuild.output.count("info me!") == 1

        getLogs(executer.daemonBaseDir).size() == 0 //we should connect to the foreground daemon so no log was created

        // Output is delivered asynchronously to the daemon's output, so wait for it to appear
        poll(60) { assert daemon.standardOutput.count("info me!") == 1 }
        daemon.standardOutput.count("debug me!") == 0
        daemon.standardOutput.count(DaemonMessages.ABOUT_TO_START_RELAYING_LOGS) == 0

        when:
        def debugBuild = executer.withArguments("-d").run()

        then:
        debugBuild.output.count("debug me!") == 1
        debugBuild.output.count("info me!") == 1

        poll(60) { assert daemon.standardOutput.count("info me!") == 2 }
        daemon.standardOutput.count("debug me!") == 1
        daemon.standardOutput.count(DaemonMessages.ABOUT_TO_START_RELAYING_LOGS) == 0

        cleanup:
        daemon.abort()
    }

    List<File> getLogs(File baseDir) {
        //the gradle version dir
        def daemonDir = new File(baseDir, GradleVersion.current().version)
        assert daemonDir.exists()
        daemonDir.listFiles().findAll { it.name.endsWith('.log') }
    }

    String readLog(File baseDir) {
        def logs = getLogs(baseDir)

        //assert single log
        assert logs.size() == 1

        logs[0].text
    }

    void printAllLogs(File baseDir) {
        getLogs(baseDir).each { println "\n---- ${it.name} ----\n${it.text}\n--------\n" }
    }

    File firstLog(File baseDir) {
        getLogs(baseDir)[0]
    }
}
