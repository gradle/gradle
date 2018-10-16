/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.testing

import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Subject

@Subject(LeakingProcessKillPattern)
class LeakingProcessKillPatternTest extends Specification {
    @Issue("https://github.com/gradle/ci-health/issues/138")
    def "matches Play application process started on Windows"() {
        def line = '"C:\\Program Files\\Java\\jdk1.7/bin/java.exe"    -Dhttp.port=0  -classpath "C:\\some\\ci\\workspace\\subprojects\\platform-play\\build\\tmp\\test files\\PlayDistributionAdvancedAppIntegrationTest\\can_run_play_distribution\\d3r0j\\build\\stage\\playBinary\\bin\\..\\lib\\advancedplayapp.jar" play.core.server.NettyServer '
        def projectDir = 'C:\\some\\ci\\workspace'

        expect:
        (line =~ LeakingProcessKillPattern.generate(projectDir)).find()
    }

    def "matches worker process started in test on Windows"() {
        def line = '"C:\\Program Files\\Java\\jdk1.7/bin/java.exe"    -Dorg.gradle.daemon.idletimeout=120000 -Dorg.gradle.daemon.registry.base=C:\\some\\agent\\workspace\\build\\daemon -Dorg.gradle.native.dir=C:\\some\\agent\\workspace\\intTestHomeDir\\worker-1\\native -Dorg.gradle.deprecation.trace=true -Djava.io.tmpdir=C:\\some\\agent\\workspace\\subprojects\\osgi\\build\\tmp -Dfile.encoding=windows-1252 -Dorg.gradle.classloaderscope.strict=true -ea -ea "-Dorg.gradle.appname=gradle" -classpath "C:\\some\\agent\\workspace\\subprojects\\osgi\\build\\integ test\\bin\\..\\lib\\gradle-launcher-4.5.jar" org.gradle.launcher.GradleMain --init-script "C:\\some\\agent\\workspace\\subprojects\\osgi\\build\\tmp\\test files\\OsgiPluginIntegrationSpec\\can_merge_manifests...archives_\\uz4kt\\reproducible-archives-init.gradle" --no-daemon --stacktrace --gradle-user-home C:\\some\\agent\\workspace\\intTestHomeDir\\worker-1 jar'
        def projectDir = 'C:\\some\\agent\\workspace'

        expect:
        (line =~ LeakingProcessKillPattern.generate(projectDir)).find()
    }

    def "does not match worker process started by main build VM on Windows"() {
        def line = '"C:\\Program Files\\Java\\jdk1.7\\bin\\java.exe" -Djava.security.manager=worker.org.gradle.process.internal.worker.child.BootstrapSecurityManager -Dfile.encoding=UTF-8 -Duser.country=US -Duser.language=en -Duser.variant -cp C:\\some\\agent\\.gradle\\caches\\4.4-rc-1\\workerMain\\gradle-worker.jar worker.org.gradle.process.internal.worker.GradleWorkerMain "\'Gradle Worker Daemon 318\'"'
        def projectDir = 'C:\\some\\agent\\workspace'

        expect:
        !(line =~ LeakingProcessKillPattern.generate(projectDir)).find()
    }
}
