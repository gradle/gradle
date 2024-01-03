/*
 * Copyright 2020 the original author or authors.
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

package gradlebuild.cleanup.services

import spock.lang.Specification

class LeakingProcessKillPatternTest extends Specification {

    def "matches worker process started in test on Windows"() {
        def line = '"C:\\Program Files\\Java\\jdk1.7/bin/java.exe"    -Dorg.gradle.daemon.idletimeout=120000 -Dorg.gradle.daemon.registry.base=C:\\some\\agent\\workspace\\build\\daemon -Dorg.gradle.native.dir=C:\\some\\agent\\workspace\\intTestHomeDir\\worker-1\\native -Dorg.gradle.deprecation.trace=true -Djava.io.tmpdir=C:\\some\\agent\\workspace\\subprojects\\osgi\\build\\tmp -Dfile.encoding=windows-1252 -Dorg.gradle.classloaderscope.strict=true -ea -ea "-Dorg.gradle.appname=gradle" -classpath "C:\\some\\agent\\workspace\\subprojects\\osgi\\build\\integ test\\bin\\..\\lib\\gradle-launcher-4.5.jar" org.gradle.launcher.GradleMain --init-script "C:\\some\\agent\\workspace\\subprojects\\osgi\\build\\tmp\\teŝt files\\OsgiPluginIntegrationSpec\\can_merge_manifests...archives_\\uz4kt\\reproducible-archives-init.gradle" --no-daemon --stacktrace --gradle-user-home C:\\some\\agent\\workspace\\intTestHomeDir\\worker-1 jar'
        def projectDir = 'C:\\some\\agent\\workspace'

        expect:
        (line =~ KillLeakingJavaProcesses.generateLeakingProcessKillPattern(projectDir)).find()
    }

    def "matches daemon process started by performance test on Windows"() {
        def line = 'java.exe  "C:\\Program Files\\Java\\jdk1.8\\bin\\java.exe" -Xms1536m -Xmx1536m -Dfile.encoding=windows-1252 -Duser.country=US -Duser.language=en -Duser.variant -cp P:\\subprojects\\performance\\build\\tmp\\performance-test-files\\FileSystemW.Test\\assemble_fo.hing\\fh0xl\\6.7-202010012357270000\\gradle-home\\lib\\gradle-launcher-6.7.jar org.gradle.launcher.daemon.bootstrap.GradleDaemon 6.7-20201001235727+0000'
        def projectDir = 'C:\\some\\agent\\workspace'

        expect:
        (line =~ KillLeakingJavaProcesses.generateLeakingProcessKillPattern(projectDir)).find()
    }

    def "matches daemon process with distribution full on Windows"() {
        def line = '8916 "C:\\Program Files\\Java\\open-jdk-20\\bin\\java.exe" -XX:MaxMetaspaceSize=512m -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=C:\\tcagent1\\work\\f63322e10dd6b396\\intTestHomeDir\\distributions-full --add-opens=java.base/java.util=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.lang.invoke=ALL-UNNAMED --add-opens=java.prefs/java.util.prefs=ALL-UNNAMED --add-opens=java.base/java.nio.charset=ALL-UNNAMED --add-opens=java.base/java.net=ALL-UNNAMED --add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED -Xms256m -Xmx1024m -Dfile.encoding=UTF-8 -Djava.io.tmpdir=C:\\tcagent1\\work\\f63322e10dd6b396\\subprojects\\launcher\\build\\tmp -Duser.country=US -Duser.language=en -Duser.variant -ea -cp "C:\\tcagent1\\work\\f63322e10dd6b396\\subprojects\\distributions-full\\build\\bin distribution\\lib\\gradle-launcher-8.5.jar" "-javaagent:C:\\tcagent1\\work\\f63322e10dd6b396\\subprojects\\distributions-full\\build\\bin distribution\\lib\\agents\\gradle-instrumentation-agent-8.5.jar" org.gradle.launcher.daemon.bootstrap.GradleDaemon 8.5-20230921032409+0000'
        def projectDir = 'C:\\tcagent1\\work\\f63322e10dd6b396\\'

        expect:
        (line =~ KillLeakingJavaProcesses.generateLeakingProcessKillPattern(projectDir)).find()
    }

    def "matches daemon process in intTestHomeDir"() {
        def line = '12460     "C:\\Program Files\\Java\\open-jdk-8\\bin\\java.exe" -XX:+HeapDumpOnOutOfMemoryError -Xmx1024m -Dfile.encoding=UTF-8 -Duser.country=US -Duser.language=en -Duser.variant -cp C:\\tcagent1\\work\\f63322e10dd6b396\\intTestHomeDir\\previousVersion\\4.7\\gradle-4.7\\lib\\gradle-launcher-4.7.jar org.gradle.launcher.daemon.bootstrap.GradleDaemon 4.7'
        def projectDir = 'C:\\tcagent1\\work\\f63322e10dd6b396\\'

        expect:
        (line =~ KillLeakingJavaProcesses.generateLeakingProcessKillPattern(projectDir)).find()
    }

    def "matches worker daemon process in intTestHomeDir"() {
        def line = '8252        "C:\\Program Files\\Java\\open-jdk-8\\bin\\java.exe" -Djava.security.manager=worker.org.gradle.process.internal.worker.child.BootstrapSecurityManager -Dfile.encoding=UTF-8 -Duser.country=US -Duser.language=en -Duser.variant -cp C:\\tcagent1\\work\\f63322e10dd6b396\\intTestHomeDir\\distributions-full\\caches\\4.7\\workerMain\\gradle-worker.jar worker.org.gradle.process.internal.worker.GradleWorkerMain "Gradle Worker Daemon 12"'
        def projectDir = 'C:\\tcagent1\\work\\f63322e10dd6b396\\'

        expect:
        (line =~ KillLeakingJavaProcesses.generateLeakingProcessKillPattern(projectDir)).find()
    }

    def "does not match worker process started by main build VM on Windows"() {
        def line = '"C:\\Program Files\\Java\\jdk1.7\\bin\\java.exe" -Djava.security.manager=worker.org.gradle.process.internal.worker.child.BootstrapSecurityManager -Dfile.encoding=UTF-8 -Duser.country=US -Duser.language=en -Duser.variant -cp C:\\some\\agent\\.gradle\\caches\\4.4-rc-1\\workerMain\\gradle-worker.jar worker.org.gradle.process.internal.worker.GradleWorkerMain "\'Gradle Worker Daemon 318\'"'
        def projectDir = 'C:\\some\\agent\\workspace'

        expect:
        !(line =~ KillLeakingJavaProcesses.generateLeakingProcessKillPattern(projectDir)).find()
    }

    def "matches kotlin compiler on linux"() {
        def line = '11522 /home/paul/.sdkman/candidates/java/10.0.2-oracle/bin/java -cp /home/paul/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-compiler-embeddable/1.3.11/a8db6c14f8b8ed74aa11b8379f961587b639c571/kotlin-compiler-embeddable-1.3.11.jar:/home/paul/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-reflect/1.3.11/aae7b33412715e9ed441934c4ffaad1bb80e9d36/kotlin-reflect-1.3.11.jar:/home/paul/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/1.3.11/4cbc5922a54376018307a731162ccaf3ef851a39/kotlin-stdlib-1.3.11.jar:/home/paul/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-script-runtime/1.3.11/1ef3a816aeacb9cd051b3ed37e2abf88910f1503/kotlin-script-runtime-1.3.11.jar:/home/paul/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib-common/1.3.11/d8b8e746e279f1c4f5e08bc14a96b82e6bb1de02/kotlin-stdlib-common-1.3.11.jar:/home/paul/.gradle/caches/modules-2/files-2.1/org.jetbrains/annotations/13.0/919f0dfe192fb4e063e7dacadee7f8bb9a2672a9/annotations-13.0.jar -Djava.awt.headless=true -Djava.rmi.server.hostname=127.0.0.1 -Xmx320m -Dkotlin.environment.keepalive org.jetbrains.kotlin.daemon.KotlinCompileDaemon --daemon-runFilesPath /home/paul/.kotlin/daemon --daemon-autoshutdownIdleSeconds=7200 --daemon-compilerClasspath /home/paul/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-compiler-embeddable/1.3.11/a8db6c14f8b8ed74aa11b8379f961587b639c571/kotlin-compiler-embeddable-1.3.11.jar:/home/paul/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-reflect/1.3.11/aae7b33412715e9ed441934c4ffaad1bb80e9d36/kotlin-reflect-1.3.11.jar:/home/paul/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/1.3.11/4cbc5922a54376018307a731162ccaf3ef851a39/kotlin-stdlib-1.3.11.jar:/home/paul/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-script-runtime/1.3.11/1ef3a816aeacb9cd051b3ed37e2abf88910f1503/kotlin-script-runtime-1.3.11.jar:/home/paul/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib-common/1.3.11/d8b8e746e279f1c4f5e08bc14a96b82e6bb1de02/kotlin-stdlib-common-1.3.11.jar:/home/paul/.gradle/caches/modules-2/files-2.1/org.jetbrains/annotations/13.0/919f0dfe192fb4e063e7dacadee7f8bb9a2672a9/annotations-13.0.jar'

        def projectDir = "/home/paul/src/kotlin-dsl"

        expect:
        (line =~ KillLeakingJavaProcesses.generateLeakingProcessKillPattern(projectDir)).find()
    }

    def "matches kotlin compiler on windows"() {
        def line = '"C:\\Program Files\\Java\\open-jdk-20\\bin\\java" -cp C:\\tcagent1\\work\\f63322e10dd6b396\\intTestHomeDir\\distributions-full\\caches\\modules-2\\files-2.1\\org.jetbrains.kotlin\\kotlin-compiler-embeddable\\1.9.10\\57ca1b0823ae3ecb451a97e1f8e6de0b19ea5294\\kotlin-compiler-embeddable-1.9.10.jar;C:\\tcagent1\\work\\f63322e10dd6b396\\intTestHomeDir\\distributions-full\\caches\\modules-2\\files-2.1\\org.jetbrains.kotlin\\kotlin-stdlib\\1.9.10\\72812e8a368917ab5c0a5081b56915ffdfec93b7\\kotlin-stdlib-1.9.10.jar;C:\\tcagent1\\work\\f63322e10dd6b396\\intTestHomeDir\\distributions-full\\caches\\modules-2\\files-2.1\\org.jetbrains.kotlin\\kotlin-script-runtime\\1.9.10\\398513a8534701579cccaf5953ea914416b4696c\\kotlin-script-runtime-1.9.10.jar;C:\\tcagent1\\work\\f63322e10dd6b396\\intTestHomeDir\\distributions-full\\caches\\modules-2\\files-2.1\\org.jetbrains.kotlin\\kotlin-reflect\\1.6.10\\1cbe9c92c12a94eea200d23c2bbaedaf3daf5132\\kotlin-reflect-1.6.10.jar;C:\\tcagent1\\work\\f63322e10dd6b396\\intTestHomeDir\\distributions-full\\caches\\modules-2\\files-2.1\\org.jetbrains.kotlin\\kotlin-daemon-embeddable\\1.9.10\\bda2f7daa6b89d2ded2d9e1e0b0cadaf9446ade1\\kotlin-daemon-embeddable-1.9.10.jar;C:\\tcagent1\\work\\f63322e10dd6b396\\intTestHomeDir\\distributions-full\\caches\\modules-2\\files-2.1\\org.jetbrains.intellij.deps\\trove4j\\1.0.20200330\\3afb14d5f9ceb459d724e907a21145e8ff394f02\\trove4j-1.0.20200330.jar;C:\\tcagent1\\work\\f63322e10dd6b396\\intTestHomeDir\\distributions-full\\caches\\modules-2\\files-2.1\\org.jetbrains.kotlin\\kotlin-stdlib-common\\1.9.10\\dafaf2c27f27c09220cee312df10917d9a5d97ce\\kotlin-stdlib-common-1.9.10.jar;C:\\tcagent1\\work\\f63322e10dd6b396\\intTestHomeDir\\distributions-full\\caches\\modules-2\\files-2.1\\org.jetbrains\\annotations\\13.0\\919f0dfe192fb4e063e7dacadee7f8bb9a2672a9\\annotations-13.0.jar -Djava.awt.headless=true -D$java.rmi.server.hostname=127.0.0.1 -Xmx1024m -XX:MaxMetaspaceSize=512m -Dkotlin.environment.keepalive -ea --add-exports java.base/sun.nio.ch=ALL-UNNAMED org.jetbrains.kotlin.daemon.KotlinCompileDaemon --daemon-runFilesPath C:\\Users\\tcagent1\\AppData\\Local\\kotlin\\daemon --daemon-autoshutdownIdleSeconds=7200 --daemon-compilerClasspath C:\\tcagent1\\work\\f63322e10dd6b396\\intTestHomeDir\\distributions-full\\caches\\modules-2\\files-2.1\\org.jetbrains.kotlin\\kotlin-compiler-embeddable\\1.9.10\\57ca1b0823ae3ecb451a97e1f8e6de0b19ea5294\\kotlin-compiler-embeddable-1.9.10.jar;C:\\tcagent1\\work\\f63322e10dd6b396\\intTestHomeDir\\distributions-full\\caches\\modules-2\\files-2.1\\org.jetbrains.kotlin\\kotlin-stdlib\\1.9.10\\72812e8a368917ab5c0a5081b56915ffdfec93b7\\kotlin-stdlib-1.9.10.jar;C:\\tcagent1\\work\\f63322e10dd6b396\\intTestHomeDir\\distributions-full\\caches\\modules-2\\files-2.1\\org.jetbrains.kotlin\\kotlin-script-runtime\\1.9.10\\398513a8534701579cccaf5953ea914416b4696c\\kotlin-script-runtime-1.9.10.jar;C:\\tcagent1\\work\\f63322e10dd6b396\\intTestHomeDir\\distributions-full\\caches\\modules-2\\files-2.1\\org.jetbrains.kotlin\\kotlin-reflect\\1.6.10\\1cbe9c92c12a94eea200d23c2bbaedaf3daf5132\\kotlin-reflect-1.6.10.jar;C:\\tcagent1\\work\\f63322e10dd6b396\\intTestHomeDir\\distributions-full\\caches\\modules-2\\files-2.1\\org.jetbrains.kotlin\\kotlin-daemon-embeddable\\1.9.10\\bda2f7daa6b89d2ded2d9e1e0b0cadaf9446ade1\\kotlin-daemon-embeddable-1.9.10.jar;C:\\tcagent1\\work\\f63322e10dd6b396\\intTestHomeDir\\distributions-full\\caches\\modules-2\\files-2.1\\org.jetbrains.intellij.deps\\trove4j\\1.0.20200330\\3afb14d5f9ceb459d724e907a21145e8ff394f02\\trove4j-1.0.20200330.jar;C:\\tcagent1\\work\\f63322e10dd6b396\\intTestHomeDir\\distributions-full\\caches\\modules-2\\files-2.1\\org.jetbrains.kotlin\\kotlin-stdlib-common\\1.9.10\\dafaf2c27f27c09220cee312df10917d9a5d97ce\\kotlin-stdlib-common-1.9.10.jar;C:\\tcagent1\\work\\f63322e10dd6b396\\intTestHomeDir\\distributions-full\\caches\\modules-2\\files-2.1\\org.jetbrains\\annotations\\13.0\\919f0dfe192fb4e063e7dacadee7f8bb9a2672a9\\annotations-13.0.jar'

        def projectDir = "C:\\tcagent1\\work\\f63322e10dd6b396\\"

        expect:
        (line =~ KillLeakingJavaProcesses.generateLeakingProcessKillPattern(projectDir)).find()
    }

    def "matches GradleDaemon on linux"() {
        def line = 'tcagent1 3976365  8.4 12.7 17555200 8376108 ?    Ssl  02:50  14:27 /opt/files/jdk-linux/OpenJDK17U-jdk_x64_linux_hotspot_17.0.6_10.tar.gz/bin/java -XX:MaxMetaspaceSize=2G -XX:+UseParallelGC -XX:+HeapDumpOnOutOfMemoryError --add-opens=java.base/java.util=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.lang.invoke=ALL-UNNAMED --add-opens=java.prefs/java.util.prefs=ALL-UNNAMED --add-opens=java.base/java.nio.charset=ALL-UNNAMED --add-opens=java.base/java.net=ALL-UNNAMED --add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED -Xms1536m -Xmx6g -Dfile.encoding=UTF-8 -Djava.io.tmpdir=/home/tcagent1/agent/temp/buildTmp -Duser.country=US -Duser.language=en -Duser.variant -cp /home/tcagent1/.gradle/wrapper/dists/gradle-8.4-20230818222751+0000-bin/89h6cjxiw2m9bic290x647cni/gradle-8.4-20230818222751+0000/lib/gradle-launcher-8.4.jar -javaagent:/home/tcagent1/.gradle/wrapper/dists/gradle-8.4-20230818222751+0000-bin/89h6cjxiw2m9bic290x647cni/gradle-8.4-20230818222751+0000/lib/agents/gradle-instrumentation-agent-8.4.jar org.gradle.launcher.daemon.bootstrap.GradleDaemon 8.4-20230818222751+0000'

        expect:
        (line =~ KillLeakingJavaProcesses.generateAllGradleProcessPattern()).find()
    }

    def "not matches TC agent JVM"() {
        def line = 'tcagent1  213966  0.0  0.1 3160628 102456 ?      Sl   Sep17   0:37 /opt/jdk/open-jdk-11/bin/java -ea -Xms16m -Xmx64m -cp ../launcher/lib/launcher.jar jetbrains.buildServer.agent.Launcher -ea -XX:+DisableAttachMechanism --add-opens=java.base/java.lang=ALL-UNNAMED -XX:+IgnoreUnrecognizedVMOptions -Xmx384m -Dteamcity_logs=../logs/ -Dlog4j2.configurationFile=file:../conf/teamcity-agent-log4j2.xml jetbrains.buildServer.agent.AgentMain -file ../conf/buildAgent.properties'

        expect:
        !(line =~ KillLeakingJavaProcesses.generateAllGradleProcessPattern()).find()
    }
}
