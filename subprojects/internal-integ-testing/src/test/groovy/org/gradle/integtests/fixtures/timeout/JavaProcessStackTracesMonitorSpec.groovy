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

package org.gradle.integtests.fixtures.timeout

import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import spock.lang.Specification

class JavaProcessStackTracesMonitorSpec extends Specification {

    @Requires(UnitTestPreconditions.NotWindows)
    def 'can extract process info from unix ps()'() {
        given:
        def output = '''
          PID TTY      STAT   TIME COMMAND
 1401 ?        Ss     0:05 /lib/systemd/systemd --user
 1409 ?        S      0:00 (sd-pam)
 2040 ?        Sl     8:07 /usr/lib/jvm/java-1.8.0-openjdk-amd64/jre/bin/java -ea -cp ../launcher/lib/launcher.jar jetbrains.buildServer.agent.Launcher -ea -Xmx384m -Dteamcity_logs=../logs/ -Dlog4j.configuration=file:../conf/teamcity-agent-log4j.xml jetbrains.buildServer.agent.AgentMain -file ../conf/buildAgent.properties
 2215 ?        Sl   473:09 /usr/lib/jvm/java-8-openjdk-amd64/jre/bin/java -ea -Xmx384m -Dteamcity_logs=../logs/ -Dlog4j.configuration=file:../conf/teamcity-agent-log4j.xml -classpath /home/tcagent1/agent/lib/idea-settings.jar:/home/tcagent1/agent/lib/coverage-agent-common.jar:/home/tcagent1/agent/lib/coverage-report.jar:/home/tcagent1/agent/lib/log4j-1.2.12.jar:/home/tcagent1/agent/lib/commons-httpclient-3.1.jar:/home/tcagent1/agent/lib/log4j-1.2.12-json-layout-1.0.9.jar:/home/tcagent1/agent/lib/freemarker.jar:/home/tcagent1/agent/lib/launcher-api.jar:/home/tcagent1/agent/lib/commons-io-1.3.2.jar:/home/tcagent1/agent/lib/agent-openapi.jar:/home/tcagent1/agent/lib/slf4j-api-1.7.5.jar:/home/tcagent1/agent/lib/trove-3.0.3.jar:/home/tcagent1/agent/lib/processesTerminator.jar:/home/tcagent1/agent/lib/slf4j-log4j12-1.7.5.jar:/home/tcagent1/agent/lib/trove4j.jar:/home/tcagent1/agent/lib/common-impl.jar:/home/tcagent1/agent/lib/ehcache-1.6.0-patch.jar:/home/tcagent1/agent/lib/buildAgent-updates-applying.jar:/home/tcagent1/agent/lib/agent-upgrade.jar:/home/tcagent1/agent/lib/commons-beanutils-core.jar:/home/tcagent1/agent/lib/app-wrapper.jar:/home/tcagent1/agent/lib/ehcache-1.7.2.jar:/home/tcagent1/agent/lib/jdom.jar:/home/tcagent1/agent/lib/spring-scripting/spring-scripting-jruby.jar:/home/tcagent1/agent/lib/spring-scripting/spring-scripting-bsh.jar:/home/tcagent1/agent/lib/spring-scripting/spring-scripting-groovy.jar:/home/tcagent1/agent/lib/xstream-1.4.10-custom.jar:/home/tcagent1/agent/lib/common-runtime.jar:/home/tcagent1/agent/lib/gson.jar:/home/tcagent1/agent/lib/xmlrpc-2.0.1.jar:/home/tcagent1/agent/lib/jaxen-1.1.1.jar:/home/tcagent1/agent/lib/duplicator-util.jar:/home/tcagent1/agent/lib/nuget-utils.jar:/home/tcagent1/agent/lib/annotations.jar:/home/tcagent1/agent/lib/serviceMessages.jar:/home/tcagent1/agent/lib/util.jar:/home/tcagent1/agent/lib/patches-impl.jar:/home/tcagent1/agent/lib/xml-rpc-wrapper.jar:/home/tcagent1/agent/lib/inspections-util.jar:/home/tcagent1/agent/lib/common.jar:/home/tcagent1/agent/lib/messages.jar:/home/tcagent1/agent/lib/commons-logging.jar:/home/tcagent1/agent/lib/commons-collections-3.2.2.jar:/home/tcagent1/agent/lib/openapi.jar:/home/tcagent1/agent/lib/launcher.jar:/home/tcagent1/agent/lib/agent-launcher.jar:/home/tcagent1/agent/lib/xercesImpl.jar:/home/tcagent1/agent/lib/jdk-searcher.jar:/home/tcagent1/agent/lib/resources_en.jar:/home/tcagent1/agent/lib/agent-installer-ui.jar:/home/tcagent1/agent/lib/agent.jar:/home/tcagent1/agent/lib/xpp3-1.1.4c.jar:/home/tcagent1/agent/lib/runtime-util.jar:/home/tcagent1/agent/lib/server-logging.jar:/home/tcagent1/agent/lib/commons-compress-1.9.jar:/home/tcagent1/agent/lib/commons-codec.jar:/home/tcagent1/agent/lib/joda-time.jar:/home/tcagent1/agent/lib/spring.jar:/home/tcagent1/agent/lib/xz-1.5.jar:/home/tcagent1/agent/lib/patches.jar:/home/tcagent1/agent/lib/agent-configurator.jar:/home/tcagent1/agent/lib/cloud-shared.jar jetbrains.buildServer.agent.AgentMain -file ../conf/buildAgent.properties -launcher.version 51228
 2477 ?        Sl     2:12 /opt/files/jdk-linux/jdk-8u161-linux-x64.tar.gz/bin/java -Djava.security.manager=worker.org.gradle.process.internal.worker.child.BootstrapSecurityManager -Dfile.encoding=UTF-8 -Duser.country=US -Duser.language=en -Duser.variant -cp /home/tcagent1/.gradle/caches/4.9-20180607113442+0000/workerMain/gradle-worker.jar worker.org.gradle.process.internal.worker.GradleWorkerMain 'Gradle Worker Daemon 199\'
 7367 pts/0    R+     0:00 ps x
15818 ?        Sl     2:15 /opt/jdk/oracle-jdk-8/bin/java -DintegTest.gradleHomeDir=/home/tcagent1/agent/work/668602365d1521fc/subprojects/test-kit/build/integ test -DintegTest.gradleUserHomeDir=/home/tcagent1/agent/work/668602365d1521fc/intTestHomeDir -DintegTest.toolingApiShadedJarDir=/home/tcagent1/agent/work/668602365d1521fc/subprojects/tooling-api/build/shaded-jar -Djava.security.manager=worker.org.gradle.process.internal.worker.child.BootstrapSecurityManager -Dorg.gradle.ci.agentCount=2 -Dorg.gradle.ci.agentNum=1 -Dorg.gradle.integtest.daemon.registry=/home/tcagent1/agent/work/668602365d1521fc/build/daemon -Dorg.gradle.integtest.executer=embedded -Dorg.gradle.integtest.multiversion=default -Dorg.gradle.integtest.native.toolChains=default -Dorg.gradle.integtest.versions=latest -Dorg.gradle.native=false -Dorg.gradle.test.maxParallelForks=4 -XX:+HeapDumpOnOutOfMemoryError -Xmx512m -Dfile.encoding=UTF-8 -Duser.country=US -Duser.language=en -Duser.variant -ea -cp /home/tcagent1/.gradle/caches/4.9-20180607113442+0000/workerMain/gradle-worker.jar worker.org.gradle.process.internal.worker.GradleWorkerMain 'Gradle Test Executor 61\'
24438 ?        Sl     3:46 /opt/files/jdk-linux/jdk-8u161-linux-x64.tar.gz/bin/java -Dorg.gradle.daemon.idletimeout=120000 -Dorg.gradle.daemon.registry.base=/home/tcagent1/agent/work/668602365d1521fc/build/daemon -Dorg.gradle.native.dir=/home/tcagent1/agent/work/668602365d1521fc/intTestHomeDir/worker-1/native -Dorg.gradle.deprecation.trace=true -Djava.io.tmpdir=/home/tcagent1/agent/work/668602365d1521fc/subprojects/test-kit/build/tmp -Dfile.encoding=UTF-8 -Dorg.gradle.classloaderscope.strict=true -Dgradle.internal.noSearchUpwards=true -Dorg.gradle.internal.launcher.welcomeMessageEnabled=false -ea -ea -Xmx2g -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/home/tcagent1/agent/work/668602365d1521fc/intTestHomeDir/worker-1 -Dorg.gradle.appname=gradle -classpath /home/tcagent1/agent/work/668602365d1521fc/subprojects/test-kit/build/integ test/lib/gradle-launcher-4.9.jar org.gradle.launcher.GradleMain --init-script /home/tcagent1/agent/work/668602365d1521fc/subprojects/test-kit/build/tmp/test files/GradleRunnerConsoleInputEndUserIntegrationTest/can_capture_user_in..._provided/xkpwb/testKitDirInit.gradle --no-daemon --stacktrace --gradle-user-home /home/tcagent1/agent/work/668602365d1521fc/intTestHomeDir/worker-1 --warning-mode=all build
27347 pts/0    S      0:00 bash
32167 ?        Ssl    8:50 /opt/files/jdk-linux/jdk-8u161-linux-x64.tar.gz/bin/java -XX:MaxPermSize=256m -XX:+HeapDumpOnOutOfMemoryError -Xmx2500m -Dfile.encoding=UTF-8 -Djava.io.tmpdir=/home/tcagent1/agent/temp/buildTmp -Duser.country=US -Duser.language=en -Duser.variant -cp /home/tcagent1/.gradle/wrapper/dists/gradle-4.9-20180607113442+0000-bin/6o1ijseqszb59y1oe4hyx3o6o/gradle-4.9-20180607113442+0000/lib/gradle-launcher-4.9.jar org.gradle.launcher.daemon.bootstrap.GradleDaemon 4.9-20180607113442+0000
'''
        when:
        def suspiciousDaemons = new JavaProcessStackTracesMonitor.StdoutAndPatterns(output).getSuspiciousDaemons()

        then:
        suspiciousDaemons == [
            new JavaProcessStackTracesMonitor.JavaProcessInfo('2477', '/opt/files/jdk-linux/jdk-8u161-linux-x64.tar.gz/bin/java'),
            new JavaProcessStackTracesMonitor.JavaProcessInfo('15818', '/opt/jdk/oracle-jdk-8/bin/java'),
            new JavaProcessStackTracesMonitor.JavaProcessInfo('24438', '/opt/files/jdk-linux/jdk-8u161-linux-x64.tar.gz/bin/java'),
            new JavaProcessStackTracesMonitor.JavaProcessInfo('32167', '/opt/files/jdk-linux/jdk-8u161-linux-x64.tar.gz/bin/java'),
        ]
    }

    @Requires(UnitTestPreconditions.Windows)
    def 'can extract process info from windows wmic'() {
        given:
        def output = '''
CommandLine ProcessId
\\SystemRoot\\System32\\smss.exe            244
%SystemRoot%\\system32\\csrss.exe ObjectDirectory=\\Windows SharedSection=1024,20480,768 Windows=On SubSystemType=Windows ServerDll=basesrv,1 ServerDll=winsrv:UserServerDllInitialization,3 ServerDll=winsrv:ConServerDllInitialization,2 ServerDll=sxssrv,4 ProfileControl=Off MaxRequestThreads=16               312
C:\\Windows\\system32\\svchost.exe -k LocalServiceAndNoImpersonation                           1168
c:\\salt\\nssm.exe                   1316
"C:\\Windows\\system32\\Dwm.exe"            1436
C:\\Windows\\Explorer.EXE               1460
"taskhost.exe"                    1592
"C:\\Program Files\\Java\\jdk1.8\\bin\\java" -Xrs -Djava.library.path="../launcher/lib;../launcher/bin" -classpath "../launcher/lib/wrapper.jar;../launcher/lib/launcher.jar" -Dwrapper.key="ywQQx5XMpKTXa1_y" -Dwrapper.port=32000 -Dwrapper.jvm.port.min=31000 -Dwrapper.jvm.port.max=31999 -Dwrapper.pid=1964 -Dwrapper.version="3.2.3" -Dwrapper.native_library="wrapper" -Dwrapper.service="TRUE" -Dwrapper.cpu.timeout="10" -Dwrapper.jvmid=2 org.tanukisoftware.wrapper.WrapperSimpleApp jetbrains.buildServer.agent.StandAloneLauncher -ea -Xmx512m -XX:+HeapDumpOnOutOfMemoryError -Xrs -Dlog4j.configuration=file:../conf/teamcity-agent-log4j.xml -Dteamcity_logs=../logs/ jetbrains.buildServer.agent.AgentMain -file ../conf/buildAgent.properties 8084
"C:\\Program Files\\Java\\jdk1.8\\jre\\bin\\java" -ea -Xmx512m -XX:+HeapDumpOnOutOfMemoryError -Xrs -Dlog4j.configuration=file:../conf/teamcity-agent-log4j.xml -Dteamcity_logs=../logs/ -classpath C:\\tcagent1\\lib\\agent-configurator.jar;C:\\tcagent1\\lib\\agent-installer-ui.jar;C:\\tcagent1\\lib\\agent-launcher.jar;C:\\tcagent1\\lib\\agent-openapi.jar;C:\\tcagent1\\lib\\agent-upgrade.jar;C:\\tcagent1\\lib\\agent.jar;C:\\tcagent1\\lib\\annotations.jar;C:\\tcagent1\\lib\\app-wrapper.jar;C:\\tcagent1\\lib\\buildAgent-updates-applying.jar;C:\\tcagent1\\lib\\cloud-shared.jar;C:\\tcagent1\\lib\\common-impl.jar;C:\\tcagent1\\lib\\common-runtime.jar;C:\\tcagent1\\lib\\common.jar;C:\\tcagent1\\lib\\commons-beanutils-core.jar;C:\\tcagent1\\lib\\commons-codec.jar;C:\\tcagent1\\lib\\commons-collections-3.2.2.jar;C:\\tcagent1\\lib\\commons-compress-1.9.jar;C:\\tcagent1\\lib\\commons-httpclient-3.1.jar;C:\\tcagent1\\lib\\commons-io-1.3.2.jar;C:\\tcagent1\\lib\\commons-logging.jar;C:\\tcagent1\\lib\\coverage-agent-common.jar;C:\\tcagent1\\lib\\coverage-report.jar;C:\\tcagent1\\lib\\duplicator-util.jar;C:\\tcagent1\\lib\\ehcache-1.6.0-patch.jar;C:\\tcagent1\\lib\\ehcache-1.7.2.jar;C:\\tcagent1\\lib\\freemarker.jar;C:\\tcagent1\\lib\\gson.jar;C:\\tcagent1\\lib\\idea-settings.jar;C:\\tcagent1\\lib\\inspections-util.jar;C:\\tcagent1\\lib\\jaxen-1.1.1.jar;C:\\tcagent1\\lib\\jdk-searcher.jar;C:\\tcagent1\\lib\\jdom.jar;C:\\tcagent1\\lib\\joda-time.jar;C:\\tcagent1\\lib\\launcher-api.jar;C:\\tcagent1\\lib\\launcher.jar;C:\\tcagent1\\lib\\log4j-1.2.12-json-layout-1.0.9.jar;C:\\tcagent1\\lib\\log4j-1.2.12.jar;C:\\tcagent1\\lib\\messages.jar;C:\\tcagent1\\lib\\nuget-utils.jar;C:\\tcagent1\\lib\\openapi.jar;C:\\tcagent1\\lib\\patches-impl.jar;C:\\tcagent1\\lib\\patches.jar;C:\\tcagent1\\lib\\processesTerminator.jar;C:\\tcagent1\\lib\\resources_en.jar;C:\\tcagent1\\lib\\runtime-util.jar;C:\\tcagent1\\lib\\server-logging.jar;C:\\tcagent1\\lib\\serviceMessages.jar;C:\\tcagent1\\lib\\slf4j-api-1.7.5.jar;C:\\tcagent1\\lib\\slf4j-log4j12-1.7.5.jar;C:\\tcagent1\\lib\\spring-scripting\\spring-scripting-bsh.jar;C:\\tcagent1\\lib\\spring-scripting\\spring-scripting-groovy.jar;C:\\tcagent1\\lib\\spring-scripting\\spring-scripting-jruby.jar;C:\\tcagent1\\lib\\spring.jar;C:\\tcagent1\\lib\\trove-3.0.3.jar;C:\\tcagent1\\lib\\trove4j.jar;C:\\tcagent1\\lib\\util.jar;C:\\tcagent1\\lib\\xercesImpl.jar;C:\\tcagent1\\lib\\xml-rpc-wrapper.jar;C:\\tcagent1\\lib\\xmlrpc-2.0.1.jar;C:\\tcagent1\\lib\\xpp3-1.1.4c.jar;C:\\tcagent1\\lib\\xstream-1.4.10-custom.jar;C:\\tcagent1\\lib\\xz-1.5.jar jetbrains.buildServer.agent.AgentMain -file ../conf/buildAgent.properties -launcher.version 51228 4956
usr\\bin\\mintty.exe -o AppID=GitForWindows.Bash -o RelaunchCommand="C:\\Program Files\\Git\\git-bash.exe" -o RelaunchDisplayName="Git Bash" -i /mingw64/share/git/git-for-windows.ico /usr/bin/bash --login -i      7788
\\??\\C:\\Windows\\system32\\conhost.exe "472490228-68470907838922115481214932-363220106-1296027029-1014590852-1678361664 7824
"C:\\Program Files (x86)\\Microsoft Visual Studio\\2017\\Community\\VC\\Tools\\MSVC\\14.14.26428\\bin\\HostX64\\x86\\VCTIP.EXE"  11880
"C:\\Program Files\\Java\\jdk1.8\\bin\\java.exe" -XX:MaxPermSize=256m -XX:+HeapDumpOnOutOfMemoryError -Xmx2500m -Dfile.encoding=UTF-8 -Djava.io.tmpdir=C:\\tcagent1\\temp\\buildTmp -Duser.country=US -Duser.language=en -Duser.variant -cp C:\\Users\\tcagent1\\.gradle\\wrapper\\dists\\gradle-4.9-20180624235932+0000-bin\\8osj55b0zpcwza9pdf19suxvr\\gradle-4.9-20180624235932+0000\\lib\\gradle-launcher-4.9.jar org.gradle.launcher.daemon.bootstrap.GradleDaemon 4.9-20180624235932+0000         1368
cmd /c C:\\tcagent1\\work\\668602365d1521fc\\gradlew.bat --init-script C:\\tcagent1\\plugins\\gradle-runner\\scripts\\init.gradle -PmaxParallelForks=4 -s --daemon --continue "-Djava7Home=C:\\Program Files\\Java\\jdk1.7" "-Djava9Home=C:\\Program Files\\Java\\jdk1.9" -Dorg.gradle.internal.tasks.createops "-PtestJavaHome=C:\\Program Files\\Java\\jdk1.8" -Dorg.gradle.daemon=false clean buildCacheHttp:platformTest 4100
"C:\\Program Files\\Java\\jdk1.8/bin/java.exe" -Xmx128m -Dfile.encoding=UTF-8 -XX:MaxPermSize=512m "-Djava.io.tmpdir=C:\\tcagent1\\temp\\buildTmp" "-Dorg.gradle.appname=gradlew" -classpath "C:\\tcagent1\\work\\668602365d1521fc\\\\gradle\\wrapper\\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain --init-script C:\\tcagent1\\plugins\\gradle-runner\\scripts\\init.gradle -PmaxParallelForks=4 -s --daemon --continue "-Djava7Home=C:\\Program Files\\Java\\jdk1.7" "-Djava9Home=C:\\Program Files\\Java\\jdk1.9" -Dorg.gradle.internal.tasks.createops "-PtestJavaHome=C:\\Program Files\\Java\\jdk1.8" -Dorg.gradle.daemon=false clean buildCacheHttp:platformTest 3908
'''
        when:
        def suspiciousDaemons = new JavaProcessStackTracesMonitor.StdoutAndPatterns(output).getSuspiciousDaemons()

        then:
        suspiciousDaemons == [
            new JavaProcessStackTracesMonitor.JavaProcessInfo('1368', "C:\\Program Files\\Java\\jdk1.8\\bin\\java.exe"),
            new JavaProcessStackTracesMonitor.JavaProcessInfo('3908', "C:\\Program Files\\Java\\jdk1.8/bin/java.exe")
        ]
    }

    @Requires(UnitTestPreconditions.NotWindows)
    def 'can locate jstack on Unix'() {
        expect:
        new JavaProcessStackTracesMonitor.JavaProcessInfo('0', javaCommand).jstackCommand == jstackCommand

        where:
        javaCommand                                                | jstackCommand
        '/opt/files/jdk-linux/jdk-8u161-linux-x64.tar.gz/bin/java' | '/opt/files/jdk-linux/jdk-8u161-linux-x64.tar.gz/bin/jstack'
        '/opt/jdk/oracle-jdk-8/bin/java'                           | '/opt/jdk/oracle-jdk-8/bin/jstack'
        '/opt/jdk/oracle-jdk-8/jre/bin/java'                       | '/opt/jdk/oracle-jdk-8/bin/jstack'
    }

    @Requires(UnitTestPreconditions.Windows)
    def 'can locate jstack on Windows'() {
        expect:
        new JavaProcessStackTracesMonitor.JavaProcessInfo('0', javaCommand).jstackCommand == jstackCommand

        where:
        javaCommand                                           | jstackCommand
        "C:\\Program Files\\Java\\jdk1.8\\bin\\java.exe"      | "C:\\Program Files\\Java\\jdk1.8\\bin\\jstack"
        "C:\\Program Files\\Java\\jdk1.8\\jre\\bin\\java.exe" | "C:\\Program Files\\Java\\jdk1.8\\bin\\jstack"
        "C:\\Program Files\\Java\\jdk1.8/bin/java.exe"        | "C:\\Program Files\\Java\\jdk1.8\\bin\\jstack"
    }
}
