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

package org.gradle.launcher.daemon.server.scaninfo

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.daemon.DaemonIntegrationSpec
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.integtests.fixtures.timeout.IntegrationTestTimeout
import org.gradle.launcher.daemon.client.SingleUseDaemonClient
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.util.internal.GFileUtils

@IntegrationTestTimeout(300)
class DaemonScanInfoIntegrationSpec extends DaemonIntegrationSpec {
    static final EXPIRATION_CHECK_FREQUENCY = 50
    public static final String EXPIRATION_EVENT = "expiration_event.txt"

    def "should capture basic data via the service registry"() {
        given:
        buildFile << """
        ${imports()}
        ${captureAndAssert()}
        """

        expect:
        buildSucceeds()

    }

    def "should capture basic data via when the daemon is running in continuous mode"() {
        given:
        buildFile << """
        ${imports()}
        ${captureAndAssert()}
        """

        expect:
        executer.withArguments('help', '--continuous', '-i').run().assertTasksExecuted(':help')
    }

    //Java 9 and above needs --add-opens to make environment variable mutation work
    @Requires(UnitTestPreconditions.Jdk8OrEarlier)
    def "should capture basic data when a foreground daemon runs multiple builds"() {
        given:
        buildFile << """
        ${imports()}

        ${captureTask("capture1", 1, 1)}
        ${captureTask("capture2", 2, 1)}
        """

        when:
        def daemon = startAForegroundDaemon()

        List<ExecutionResult> captureResults = []
        captureResults << executer.withTasks('capture1').run()
        captureResults << executer.withTasks('capture2').run()

        then:
        captureResults[0].assertTaskExecuted(':capture1')
        captureResults[1].assertTaskExecuted(':capture2')

        cleanup:
        daemon?.abort()
    }

    def "a daemon expiration listener receives expiration reasons continuous:#continuous"() {
        given:
        buildFile << """
           ${imports()}
           ${registerTestExpirationStrategy()}
           ${registerExpirationListener()}
           ${waitForExpirationTask()}
        """

        when:
        openJpmsModulesForConfigurationCache()
        if (continuous) {
            executer.withArgument('waitForExpiration')
            executer.withArgument('--continuous')
        } else {
            executer.withArgument('waitForExpiration')
        }
        executer.run()

        then:
        file(EXPIRATION_EVENT).text.startsWith "onExpirationEvent fired with: expiring daemon with TestExpirationStrategy uuid:"

        where:
        continuous << [true, false]
    }

    def "daemon expiration listener is implicitly for the current build only"() {
        given:
        buildFile << """
           ${imports()}
           ${registerTestExpirationStrategy()}
           ${registerExpirationListener()}
           ${waitForExpirationTask()}
        """

        when:
        openJpmsModulesForConfigurationCache()
        executer.withArgument('waitForExpiration').run()

        then:
        file(EXPIRATION_EVENT).text.startsWith "onExpirationEvent fired with: expiring daemon with TestExpirationStrategy uuid:"

        when:
        GFileUtils.forceDelete(file(EXPIRATION_EVENT))
        buildFile.text = """
           ${imports()}
           ${waitForExpirationTask()}
        """
        openJpmsModulesForConfigurationCache()
        def waitForExpirationResult = executer.withArgument('waitForExpiration').runWithFailure()

        then:
        waitForExpirationResult.assertHasCause("Timed out waiting for expiration event")

        and:
        !file(EXPIRATION_EVENT).exists()
    }

    def "a daemon expiration listener receives expiration reasons when daemons run in the foreground"() {
        given:
        buildFile << """
           ${imports()}
           ${registerTestExpirationStrategy()}
           ${registerExpirationListener()}
           ${waitForExpirationTask()}
        """

        when:
        startAForegroundDaemon()
        openJpmsModulesForConfigurationCache()
        executer.withTasks('waitForExpiration').run()

        then:
        file(EXPIRATION_EVENT).text.startsWith "onExpirationEvent fired with: expiring daemon with TestExpirationStrategy uuid:"
    }

    @Requires(IntegTestPreconditions.NotDaemonExecutor)
    def "captures single use daemons"() {
        setup:
        file('gradle.properties') << "org.gradle.jvmargs=-Xmx64m"

        buildFile << """
        ${imports()}

        ${captureTask("capture", 1, 1, true)}
        """

        when:
        result = executer.withArgument('--no-daemon').withTasks('capture').run()

        then:
        executed(':capture')
        outputContains(SingleUseDaemonClient.MESSAGE)

        and:
        daemons.daemon.stops()
    }

    static String captureTask(String name, int buildCount, int daemonCount, boolean singleUse = false) {
        """
    task $name {
        doLast {
            DaemonScanInfo info = services.get(DaemonScanInfo)
            ${assertInfo(buildCount, daemonCount, singleUse)}
        }
    }
    """
    }

    static String captureAndAssert() {
        return """
           DaemonScanInfo info = services.get(DaemonScanInfo)
           ${assertInfo(1, 1)}
           """
    }

    static String assertInfo(int numberOfBuilds, int numDaemons, boolean singleUse = false) {
        return """
           assert info.getNumberOfBuilds() == ${numberOfBuilds}
           assert info.getNumberOfRunningDaemons() == ${numDaemons}
           assert info.getIdleTimeout() == 120000
           assert info.getStartedAt() <= System.currentTimeMillis() + 1000 //accept slight clock adjustments while the test is running
           assert info.isSingleUse() == ${singleUse}
        """
    }

    static String waitForExpirationTask() {
        """
        task waitForExpiration {
            doFirst {
                if (!latch.await(2, TimeUnit.SECONDS)) {
                    throw new GradleException("Timed out waiting for expiration event")
                }
            }
        }
        """
    }

    static String registerExpirationListener() {
        """
        def daemonScanInfo = services.get(DaemonScanInfo)

        daemonScanInfo.notifyOnUnhealthy(new Action<String>() {
            @Override
            public void execute(String s) {
                  println "onExpirationEvent fired with: \${s}"
                  file("${EXPIRATION_EVENT}").text = "onExpirationEvent fired with: \${s}"
                  latch.countDown()
            }
        })
        """
    }

    static String registerTestExpirationStrategy() {
        """
        class TestExpirationStrategy implements DaemonExpirationStrategy {
            Project project

            public TestExpirationStrategy(Project project){
                this.project = project
            }

            @Override
            public DaemonExpirationResult checkExpiration() {
                DaemonContext dc = null
                try {
                    dc = services.get(DaemonContext)
                } catch (Exception e) {
                    // ignore
                }
                return new DaemonExpirationResult(DaemonExpirationStatus.GRACEFUL_EXPIRE, "expiring daemon with TestExpirationStrategy uuid: \${dc?.getUid()}")
            }
        }

        def daemon =  services.get(Daemon)
        daemon.scheduleExpirationChecks(new AllDaemonExpirationStrategy([new TestExpirationStrategy(project)]), $EXPIRATION_CHECK_FREQUENCY)
        """
    }

    static String imports() {
        """
        import org.gradle.launcher.daemon.server.scaninfo.DaemonScanInfo
        import org.gradle.launcher.daemon.context.*
        import org.gradle.launcher.daemon.server.*
        import org.gradle.launcher.daemon.server.expiry.*
        import java.util.concurrent.CountDownLatch
        import java.util.concurrent.TimeUnit

        def latch = new CountDownLatch(1)
        """
    }

    private void openJpmsModulesForConfigurationCache() {
        if (JavaVersion.current().isJava9Compatible() && GradleContextualExecuter.isConfigCache()) {
            // For java.util.concurrent.CountDownLatch being serialized reflectively by configuration cache
            executer.withArgument('-Dorg.gradle.jvmargs=--add-opens java.base/java.util.concurrent=ALL-UNNAMED --add-opens java.base/java.util.concurrent.locks=ALL-UNNAMED')
        }
    }

}
