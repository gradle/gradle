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

import org.gradle.launcher.daemon.client.DaemonDisappearedException
import org.gradle.launcher.daemon.logging.DaemonMessages
import org.gradle.util.TextUtil
import spock.lang.Timeout

import static org.gradle.test.fixtures.ConcurrentTestUtil.poll

/**
 * by Szczepan Faber, created at: 1/20/12
 */
class DaemonFeedbackIntegrationSpec extends DaemonIntegrationSpec {
    def setup() {
        executer.requireIsolatedDaemons()
    }

    def "daemon keeps logging to the file even if the build is started"() {
        given:
        file("build.gradle") << """
task sleep << {
    println 'taking a nap...'
    Thread.sleep(10000)
    println 'finished the nap...'
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

        def log = readLog(executer.daemonBaseDir)
        assert log.contains(DaemonMessages.REMOVING_PRESENCE_DUE_TO_STOP)
        assert log.contains(DaemonMessages.DAEMON_VM_SHUTTING_DOWN)
    }

    @Timeout(25)
    def "promptly shows decent message when daemon cannot be started"() {
        when:
        executer.withArguments("-Dorg.gradle.jvmargs=-Xyz").run()

        then:
        def ex = thrown(Exception)
        ex.message.contains(DaemonMessages.UNABLE_TO_START_DAEMON)
        ex.message.contains("-Xyz")
    }

    @Timeout(25)
    def "promptly shows decent message when awkward java home used"() {
        def dummyJdk = file("dummyJdk").createDir()
        assert dummyJdk.isDirectory()
        def jdkPath = TextUtil.escapeString(dummyJdk.canonicalPath)
        
        when:
        executer.withArguments("-Dorg.gradle.java.home=$jdkPath").run()

        then:
        def ex = thrown(Exception)
        ex.message.contains('org.gradle.java.home')
        ex.message.contains(jdkPath)
    }

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
        file("build.gradle") << "task foo << { println 'hey!' }"

        when: "runing build with --info"
        executer.withArguments("-i").withTasks('foo').run()

        then:
        def log = readLog(executer.daemonBaseDir)
        log.findAll(DaemonMessages.STARTED_EXECUTING_COMMAND).size() == 1

        poll(60) {
            //in theory the client could have received result and complete
            // but the daemon has not yet finished processing hence polling
            def daemonLog = readLog(executer.daemonBaseDir)
            daemonLog.findAll(DaemonMessages.FINISHED_EXECUTING_COMMAND).size() == 1
            daemonLog.findAll(DaemonMessages.FINISHED_BUILD).size() == 1
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

    def "disappearing daemon makes client log useful information"() {
        given:
        file("build.gradle") << "System.exit(0)"

        when:
        def failure = executer.withArguments("-q").runWithFailure()

        then:
        failure.error.contains(DaemonDisappearedException.MESSAGE)
        failure.error.contains(DaemonMessages.DAEMON_VM_SHUTTING_DOWN)
    }

    def "foreground daemon log honors log levels for logging"() {
        given:
        file("build.gradle") << """
            logger.debug('debug me!')
            logger.info('info me!')
        """

        when:
        def daemon = executer.setAllowExtraLogging(false).withArguments("--foreground").start()
        
        then:
        poll(60) { assert daemon.standardOutput.contains(DaemonMessages.PROCESS_STARTED) }

        when:
        def infoBuild = executer.withArguments("-i", "-Dorg.gradle.jvmargs=-ea").run()

        then:
        getLogs(executer.daemonBaseDir).size() == 0 //we should connect to the foreground daemon so no log was created

        daemon.standardOutput.count(DaemonMessages.ABOUT_TO_START_RELAYING_LOGS) == 0
        daemon.standardOutput.count("info me!") == 1

        infoBuild.output.count("debug me!") == 0
        infoBuild.output.count("info me!") == 1

        when:
        def debugBuild = executer.withArguments("-d", "-Dorg.gradle.jvmargs=-ea").run()

        then:
        daemon.standardOutput.count(DaemonMessages.ABOUT_TO_START_RELAYING_LOGS) == 0
        daemon.standardOutput.count("debug me!") == 1

        debugBuild.output.count("debug me!") == 1
    }

    List<File> getLogs(baseDir) {
        //the gradle version dir
        assert baseDir.listFiles().length == 1
        def daemonFiles = baseDir.listFiles()[0].listFiles()

        daemonFiles.findAll { it.name.endsWith('.log') }
    }

    String readLog(baseDir) {
        def logs = getLogs(baseDir)

        //assert single log
        assert logs.size() == 1

        logs[0].text
    }
    
    void printAllLogs(baseDir) {
        getLogs(baseDir).each { println "\n---- ${it.name} ----\n${it.text}\n--------\n" }
    }

    File firstLog(baseDir) {
        getLogs(baseDir)[0]
    }
}
