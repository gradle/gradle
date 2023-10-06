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

import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.execution.RunRootBuildWorkBuildOperationType
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.internal.enterprise.core.GradleEnterprisePluginManager
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
    final String packageName = 'com.gradle.enterprise.gradleplugin'
    final String simpleClassName = 'GradleEnterprisePlugin'
    final String className = "${packageName}.${simpleClassName}"

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

    String plugins() {
        """
            plugins { id "$id" version "$runtimeVersion" }
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
        builder.packageName = packageName
        builder.addPluginSource(id, simpleClassName, """
            package $builder.packageName

            class ${simpleClassName} implements $Plugin.name<$Settings.name> {
                void apply($Settings.name settings) {
                    println "gradleEnterprisePlugin.apply.runtimeVersion = $runtimeVersion"

                    if (!$doCheckIn || settings.gradle.parent != null) {
                        return
                    }

                    def pluginMetadata = { -> "$runtimeVersion" } as $GradleEnterprisePluginMetadata.name
                    def serviceFactory = {
                        $GradleEnterprisePluginConfig.name config,
                        $GradleEnterprisePluginRequiredServices.name requiredServices,
                        $GradleEnterprisePluginBuildState.name buildState ->

                        println "gradleEnterprisePlugin.serviceFactoryCreate.config.buildScanRequest = \$config.buildScanRequest"
                        println "gradleEnterprisePlugin.serviceFactoryCreate.config.autoApplied = \$config.autoApplied"
                        println "gradleEnterprisePlugin.serviceFactoryCreate.config.taskExecutingBuild = \$config.taskExecutingBuild"

                        println "gradleEnterprisePlugin.serviceFactoryCreate.buildState.buildStartedTime = \$buildState.buildStartedTime"
                        println "gradleEnterprisePlugin.serviceFactoryCreate.buildState.currentTime = \$buildState.currentTime"
                        println "gradleEnterprisePlugin.serviceFactoryCreate.buildState.buildInvocationId = \$buildState.buildInvocationId"
                        println "gradleEnterprisePlugin.serviceFactoryCreate.buildState.workspaceId = \$buildState.workspaceId"
                        println "gradleEnterprisePlugin.serviceFactoryCreate.buildState.userId = \$buildState.userId"
                        println "gradleEnterprisePlugin.serviceFactoryCreate.buildState.daemonScanInfo.numberOfBuilds = \${buildState.daemonScanInfo?.numberOfBuilds}"


                        new $GradleEnterprisePluginService.name() {

                            Externalizable nonSerializable = new Externalizable() {
                                void writeExternal(ObjectOutput out) {
                                    throw new IOException("can't be serialized")
                                }

                                void readExternal(ObjectInput input) throws IOException, ClassNotFoundException {
                                    throw new IOException("can't be serialized")
                                }
                            }

                            $GradleEnterprisePluginConfig.name _config = config
                            $GradleEnterprisePluginRequiredServices.name _requiredServices = requiredServices
                            $GradleEnterprisePluginBuildState.name _buildState = buildState

                            $BuildOperationNotificationListener.name getBuildOperationNotificationListener() {
                                new $BuildOperationNotificationListener.name() {
                                    void started($BuildOperationStartedNotification.name notification) {
                                        if (notification.notificationOperationDetails instanceof ${RunRootBuildWorkBuildOperationType.Details.name}) {
                                            println "gradleEnterprisePlugin.buildOperationNotificationListener.received = true"
                                        }
                                    }
                                    void progress($BuildOperationProgressNotification.name notification) {}
                                    void finished($BuildOperationFinishedNotification.name notification) {}
                                }
                            }

                            $GradleEnterprisePluginEndOfBuildListener.name getEndOfBuildListener() {
                                return { $GradleEnterprisePluginEndOfBuildListener.BuildResult.name buildResult ->
                                    println "gradleEnterprisePlugin.endOfBuild.buildResult.failure = \$buildResult.failure"
                                    if (System.getProperty("build-listener-failure") != null) {
                                        throw new RuntimeException("broken")
                                    }
                                } as $GradleEnterprisePluginEndOfBuildListener.name
                            }
                        }

                    } as $GradleEnterprisePluginServiceFactory.name

                    def checkInService = settings.gradle.services.get($GradleEnterprisePluginCheckInService.name)

                    def result = checkInService.checkIn(pluginMetadata, serviceFactory)
                    if (result.unsupportedMessage == null) {
                        println "gradleEnterprisePlugin.checkIn.supported"
                        settings.gradle.extensions.add("serviceRef", result.pluginServiceRef)
                    } else {
                        println "gradleEnterprisePlugin.checkIn.unsupported.reasonMessage = \$result.unsupportedMessage"
                    }
                }
            }
        """)

        builder.addPlugin("", "com.gradle.build-scan", 'BuildScanPlugin')

        builder.publishAs("${AutoAppliedGradleEnterprisePlugin.GROUP}:${AutoAppliedGradleEnterprisePlugin.NAME}:${artifactVersion}", mavenRepo, pluginBuildExecuter)
    }

    void assertBuildScanRequest(String output, GradleEnterprisePluginConfig.BuildScanRequest buildScanRequest) {
        assert output.contains("gradleEnterprisePlugin.serviceFactoryCreate.config.buildScanRequest = $buildScanRequest")
    }

    void assertAutoApplied(String output, boolean autoApplied) {
        assert output.contains("gradleEnterprisePlugin.serviceFactoryCreate.config.autoApplied = $autoApplied")
    }

    void assertUnsupportedMessage(String output, String unsupported) {
        assert output.contains("gradleEnterprisePlugin.checkIn.unsupported.reasonMessage = $unsupported")
    }

    void assertEndOfBuildWithFailure(String output, @Nullable String failure) {
        assert output.count("gradleEnterprisePlugin.endOfBuild.buildResult.failure = ") == 1
        assert output.contains("gradleEnterprisePlugin.endOfBuild.buildResult.failure = $failure")
    }

    void receivedBuildOperationNotifications(String output) {
        assert output.contains("gradleEnterprisePlugin.buildOperationNotificationListener.received = true")
    }

    void issuedNoPluginWarning(String output) {
        assert output.contains(GradleEnterprisePluginManager.NO_SCAN_PLUGIN_MSG)
    }

    void didNotIssuedNoPluginWarning(String output) {
        assert !output.contains(GradleEnterprisePluginManager.NO_SCAN_PLUGIN_MSG)
    }

    void issuedNoPluginWarningCount(String output, int count) {
        assert output.count(GradleEnterprisePluginManager.NO_SCAN_PLUGIN_MSG) == count
    }

    void serviceCreatedOnce(String output) {
        assert output.count("gradleEnterprisePlugin.serviceFactoryCreate.config.buildScanRequest") == 1
    }

    void appliedOnce(String output) {
        assert output.count("gradleEnterprisePlugin.apply.runtimeVersion = $runtimeVersion") == 1
    }

    void notApplied(String output) {
        assert !output.contains("gradleEnterprisePlugin.apply.runtimeVersion = $runtimeVersion")
    }

    void assertBackgroundJobCompletedBeforeShutdown(String output, String expectedJobOutput) {
        def jobOutputPosition = output.indexOf(expectedJobOutput)
        assert jobOutputPosition >= 0 : "cannot find $expectedJobOutput"
        assert jobOutputPosition < output.indexOf("gradleEnterprisePlugin.endOfBuild.buildResult.failure")
    }
}
