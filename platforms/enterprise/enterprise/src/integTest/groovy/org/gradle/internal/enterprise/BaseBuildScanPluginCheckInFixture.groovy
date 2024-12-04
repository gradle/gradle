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
import org.gradle.plugin.management.internal.autoapply.AutoAppliedDevelocityPlugin
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.maven.MavenFileRepository
import org.gradle.test.fixtures.plugin.PluginBuilder

import javax.annotation.Nullable

@SuppressWarnings("GrMethodMayBeStatic")
abstract class BaseBuildScanPluginCheckInFixture {

    private final TestFile projectDir
    private final MavenFileRepository mavenRepo
    private final GradleExecuter pluginBuildExecuter

    String runtimeVersion = AutoAppliedDevelocityPlugin.VERSION
    String artifactVersion = AutoAppliedDevelocityPlugin.VERSION

    final String id
    final String packageName
    final String simpleClassName
    final String className
    final String pluginArtifactGroup = AutoAppliedDevelocityPlugin.GROUP
    final String pluginArtifactName

    boolean doCheckIn = true
    protected boolean added

    BaseBuildScanPluginCheckInFixture(
        TestFile projectDir,
        MavenFileRepository mavenRepo,
        GradleExecuter pluginBuildExecuter,
        String pluginId,
        String pluginPackageName,
        String pluginSimpleClassName,
        String pluginArtifactName
    ) {
        this.projectDir = projectDir
        this.mavenRepo = mavenRepo
        this.pluginBuildExecuter = pluginBuildExecuter
        this.id = pluginId
        this.packageName = pluginPackageName
        this.simpleClassName = pluginSimpleClassName
        this.className = "${packageName}.${simpleClassName}"
        this.pluginArtifactName = pluginArtifactName
    }

    String pluginManagement() {
        """
            pluginManagement {
                repositories {
                    maven { url = '${mavenRepo.uri}' }
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

    private String getPropertyPrefix() {
        simpleClassName.uncapitalize()
    }

    void publishDummyPluginNow() {
        if (added) {
            return
        }
        added = true
        def builder = new PluginBuilder(projectDir.file('plugin-' + id))
        builder.packageName = packageName
        builder.addPluginSource(id, simpleClassName, """
            package $builder.packageName

            class ${simpleClassName} implements $Plugin.name<$Settings.name> {
                void apply($Settings.name settings) {
                    println "${propertyPrefix}.apply.runtimeVersion = $runtimeVersion"

                    if (!$doCheckIn || settings.gradle.parent != null) {
                        return
                    }

                    def pluginMetadata = { -> "$runtimeVersion" } as $GradleEnterprisePluginMetadata.name
                    def serviceFactory = {
                        $GradleEnterprisePluginConfig.name config,
                        $GradleEnterprisePluginRequiredServices.name requiredServices,
                        $GradleEnterprisePluginBuildState.name buildState ->

                        println "${propertyPrefix}.serviceFactoryCreate.config.buildScanRequest = \$config.buildScanRequest"
                        println "${propertyPrefix}.serviceFactoryCreate.config.autoApplied = \$config.autoApplied"
                        println "${propertyPrefix}.serviceFactoryCreate.config.taskExecutingBuild = \$config.taskExecutingBuild"

                        println "${propertyPrefix}.serviceFactoryCreate.buildState.buildStartedTime = \$buildState.buildStartedTime"
                        println "${propertyPrefix}.serviceFactoryCreate.buildState.currentTime = \$buildState.currentTime"
                        println "${propertyPrefix}.serviceFactoryCreate.buildState.buildInvocationId = \$buildState.buildInvocationId"
                        println "${propertyPrefix}.serviceFactoryCreate.buildState.workspaceId = \$buildState.workspaceId"
                        println "${propertyPrefix}.serviceFactoryCreate.buildState.userId = \$buildState.userId"
                        println "${propertyPrefix}.serviceFactoryCreate.buildState.daemonScanInfo.numberOfBuilds = \${buildState.daemonScanInfo?.numberOfBuilds}"


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
                                            println "${propertyPrefix}.buildOperationNotificationListener.received = true"
                                        }
                                    }
                                    void progress($BuildOperationProgressNotification.name notification) {}
                                    void finished($BuildOperationFinishedNotification.name notification) {}
                                }
                            }

                            $GradleEnterprisePluginEndOfBuildListener.name getEndOfBuildListener() {
                                return { $GradleEnterprisePluginEndOfBuildListener.BuildResult.name buildResult ->
                                    println "${propertyPrefix}.endOfBuild.buildResult.failure = \$buildResult.failure"
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
                        println "${propertyPrefix}.checkIn.supported"
                        settings.gradle.extensions.add("serviceRef", result.pluginServiceRef)
                    } else {
                        println "${propertyPrefix}.checkIn.unsupported.reasonMessage = \$result.unsupportedMessage"
                    }
                }
            }
        """)

        builder.addPlugin("", "com.gradle.build-scan", 'BuildScanPlugin')

        builder.publishAs("${pluginArtifactGroup}:${pluginArtifactName}:${artifactVersion}", mavenRepo, pluginBuildExecuter)
    }

    void assertBuildScanRequest(String output, GradleEnterprisePluginConfig.BuildScanRequest buildScanRequest) {
        assert output.contains("${propertyPrefix}.serviceFactoryCreate.config.buildScanRequest = $buildScanRequest")
    }

    void assertAutoApplied(String output, boolean autoApplied) {
        assert output.contains("${propertyPrefix}.serviceFactoryCreate.config.autoApplied = $autoApplied")
    }

    void assertUnsupportedMessage(String output, String unsupported) {
        assert output.contains("${propertyPrefix}.checkIn.unsupported.reasonMessage = $unsupported")
    }

    void assertEndOfBuildWithFailure(String output, @Nullable String failure) {
        assert output.count("${propertyPrefix}.endOfBuild.buildResult.failure = ") == 1
        assert output.contains("${propertyPrefix}.endOfBuild.buildResult.failure = $failure")
    }

    void receivedBuildOperationNotifications(String output) {
        assert output.contains("${propertyPrefix}.buildOperationNotificationListener.received = true")
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
        assert output.count("${propertyPrefix}.serviceFactoryCreate.config.buildScanRequest") == 1
    }

    void appliedOnce(String output) {
        assert output.count("${propertyPrefix}.apply.runtimeVersion = $runtimeVersion") == 1
    }

    void notApplied(String output) {
        assert !output.contains("${propertyPrefix}.apply.runtimeVersion = $runtimeVersion")
    }

    void assertBackgroundJobCompletedBeforeShutdown(String output, String expectedJobOutput) {
        def jobOutputPosition = output.indexOf(expectedJobOutput)
        assert jobOutputPosition >= 0: "cannot find $expectedJobOutput"
        assert jobOutputPosition < output.indexOf("${propertyPrefix}.endOfBuild.buildResult.failure")
    }
}
