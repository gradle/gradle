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

package org.gradle.internal.enterprise

import org.gradle.api.provider.Provider
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.internal.enterprise.core.GradleEnterprisePluginPresence
import org.gradle.internal.operations.notify.BuildOperationFinishedNotification
import org.gradle.internal.operations.notify.BuildOperationNotificationListener
import org.gradle.internal.operations.notify.BuildOperationProgressNotification
import org.gradle.internal.operations.notify.BuildOperationStartedNotification
import org.gradle.plugin.management.internal.autoapply.AutoAppliedGradleEnterprisePlugin
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.maven.MavenFileRepository
import org.gradle.test.fixtures.plugin.PluginBuilder

import javax.annotation.Nullable

@SuppressWarnings("GrMethodMayBeStatic")
class GradleEnterprisePluginCheckInFixture {

    private final TestFile projectDir
    private final MavenFileRepository mavenRepo
    private final GradleExecuter pluginBuildExecuter

    String runtimeVersion = AutoAppliedGradleEnterprisePlugin.VERSION
    String artifactVersion = AutoAppliedGradleEnterprisePlugin.VERSION
    final String id = AutoAppliedGradleEnterprisePlugin.ID.id

    boolean doCheckIn = true
    protected boolean added

    GradleEnterprisePluginCheckInFixture(TestFile projectDir, MavenFileRepository mavenRepo, GradleExecuter pluginBuildExecuter) {
        this.projectDir = projectDir
        this.mavenRepo = mavenRepo
        this.pluginBuildExecuter = pluginBuildExecuter
    }

    String pluginManagement() {
        """
            pluginManagement {
                repositories {
                    maven { url '${mavenRepo.uri}' }
                }
            }
        """
    }

    void publishDummyPlugin(GradleExecuter executer) {
        executer.beforeExecute {
            publishDummyPluginNow()
        }
    }

    void publishDummyPluginNow() {
        if (added) {
            return
        }
        added = true
        def builder = new PluginBuilder(projectDir.file('plugin-' + AutoAppliedGradleEnterprisePlugin.ID.id))
        builder.addSettingsPlugin("""

            println "gradleEnterprisePlugin.apply.runtimeVersion = $runtimeVersion"

            if (!$doCheckIn) {
                return
            }

            def pluginMetadata = { -> "$runtimeVersion" } as $GradleEnterprisePluginMetadata.name
            def serviceFactory = {
                $GradleEnterprisePluginConfig.name config,
                $GradleEnterprisePluginRequiredServices.name requiredServices,
                $GradleEnterprisePluginBuildState.name buildState ->

                println "gradleEnterprisePlugin.checkIn.config.buildScanRequest = \$config.buildScanRequest"
                println "gradleEnterprisePlugin.checkIn.config.taskExecutingBuild = \$config.taskExecutingBuild"

                new $GradleEnterprisePluginService.name() {
                    $BuildOperationNotificationListener.name getBuildOperationNotificationListener() {
                        new $BuildOperationNotificationListener.name() {
                            void started($BuildOperationStartedNotification.name notification) {}
                            void progress($BuildOperationProgressNotification.name notification) {}
                            void finished($BuildOperationFinishedNotification.name notification) {}
                        }
                    }

                    $GradleEnterprisePluginEndOfBuildListener.name getEndOfBuildListener() {
                        return { $GradleEnterprisePluginEndOfBuildListener.BuildResult.name buildResult ->
                            println "gradleEnterprisePlugin.endOfBuild.buildResult.failure = \$buildResult.failure"
                        } as $GradleEnterprisePluginEndOfBuildListener.name
                    }
                }

            } as $GradleEnterprisePluginServiceFactory.name

            def resultHandler = new $GradleEnterprisePluginCheckInResultHandler.name() {
                void unsupported(String reasonMessage) {
                    println "gradleEnterprisePlugin.checkIn.unsupported.reasonMessage = \$reasonMessage"
                }

                void supported($Provider.name<$GradleEnterprisePluginService.name> serviceProvider) {
                    println "gradleEnterprisePlugin.checkIn.supported"
                }
            }

            def checkInService = settings.gradle.services.get($GradleEnterprisePluginCheckInService.name)

            checkInService.checkIn(pluginMetadata, serviceFactory, resultHandler)
        """, AutoAppliedGradleEnterprisePlugin.ID.id, 'GradleEnterprisePlugin')

        builder.addPlugin("""
        """, "com.gradle.build-scan", 'BuildScanPlugin')

        builder.publishAs("com.gradle:gradle-enterprise-gradle-plugin:${artifactVersion}", mavenRepo, pluginBuildExecuter)
    }

    void assertBuildScanRequest(String output, GradleEnterprisePluginConfig.BuildScanRequest buildScanRequest) {
        assert output.contains("gradleEnterprisePlugin.checkIn.config.buildScanRequest = $buildScanRequest")
    }

    void assertUnsupportedMessage(String output, String unsupported) {
        assert output.contains("gradleEnterprisePlugin.checkIn.unsupported.reasonMessage = $unsupported")
    }

    void assertEndOfBuildWithFailure(String output, @Nullable String failure) {
        assert output.contains("gradleEnterprisePlugin.endOfBuild.buildResult.failure = $failure")
    }

    void issuedNoPluginWarning(String output) {
        assert output.contains(GradleEnterprisePluginPresence.NO_SCAN_PLUGIN_MSG)
    }

    void didNotIssuedNoPluginWarning(String output) {
        assert !output.contains(GradleEnterprisePluginPresence.NO_SCAN_PLUGIN_MSG)
    }

    void issuedNoPluginWarningCount(String output, int count) {
        assert output.count(GradleEnterprisePluginPresence.NO_SCAN_PLUGIN_MSG) == count
    }

    void appliedOnce(String output) {
        assert output.count("gradleEnterprisePlugin.apply.runtimeVersion = $runtimeVersion") == 1
    }

    void notApplied(String output) {
        assert !output.contains("gradleEnterprisePlugin.apply.runtimeVersion = $runtimeVersion")
    }

}
