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

package org.gradle.internal.enterprise.legacy

import groovy.json.JsonSlurper
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.internal.enterprise.core.GradleEnterprisePluginManager
import org.gradle.internal.scan.config.BuildScanConfig
import org.gradle.plugin.management.internal.autoapply.AutoAppliedGradleEnterprisePlugin
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.maven.MavenFileRepository
import org.gradle.test.fixtures.plugin.PluginBuilder

@SuppressWarnings("GrMethodMayBeStatic")
class GradleEnterprisePluginLegacyContactPointFixture {

    public static final String PLUGIN_NOT_APPLIED_MSG = GradleEnterprisePluginManager.NO_SCAN_PLUGIN_MSG
    public static final String GRADLE_ENTERPRISE_PLUGIN_ID = AutoAppliedGradleEnterprisePlugin.ID.id
    public static final String PUBLISHING_BUILD_SCAN_MESSAGE_PREFIX = 'PUBLISHING BUILD SCAN v'
    public static final String BUILD_SCAN_PLUGIN_APPLIED_MESSAGE = 'APPLIED OLD BUILD SCAN PLUGIN'

    public static final String GRADLE_ENTERPRISE_PLUGIN_CLASS_SIMPLE_NAME = 'GradleEnterprisePlugin'

    public static final String BUILD_SCAN_PLUGIN_ID = "com.gradle.build-scan"
    public static final String BUILD_SCAN_PLUGIN_CLASS_SIMPLE_NAME = 'BuildScanPlugin'

    private final TestFile projectDir
    private final MavenFileRepository mavenRepo
    private final GradleExecuter pluginBuildExecuter

    boolean collectConfig = true
    boolean logConfig
    boolean logApplied

    protected boolean added

    String runtimeVersion = AutoAppliedGradleEnterprisePlugin.VERSION
    String artifactVersion = AutoAppliedGradleEnterprisePlugin.VERSION

    GradleEnterprisePluginLegacyContactPointFixture(TestFile projectDir, MavenFileRepository mavenRepo, GradleExecuter pluginBuildExecuter) {
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

        def buildFile = projectDir.file("build.gradle")
        buildFile << """
            org.gradle.internal.scan.config.BuildScanPluginMetadata buildScanPluginMetadata = { "${runtimeVersion}" } as org.gradle.internal.scan.config.BuildScanPluginMetadata
            def buildScanPluginConfig
            if ($collectConfig) {
                def c = project.gradle.services.get(org.gradle.internal.scan.config.BuildScanConfigProvider).collect(buildScanPluginMetadata)
                buildScanPluginConfig = c
                if ($logConfig) {
                    println "buildScan.enabled: " + c.enabled
                    println "buildScan.disabled: " + c.disabled
                    println "buildScan.unsupportedMessage: " + c.unsupportedMessage
                    println "buildScan.attributes: " + groovy.json.JsonOutput.toJson(c.attributes)
                }
            }
        """
        if (logApplied) {
            buildFile << """
                def pluginApplied = services.get(${GradleEnterprisePluginManager.name}).isPresent()
                println "buildScan plugin applied: " + pluginApplied
            """
        }

        def builder = new PluginBuilder(projectDir.file('plugin-' + AutoAppliedGradleEnterprisePlugin.ID.id))
        builder.addSettingsPlugin("""
            println '${PUBLISHING_BUILD_SCAN_MESSAGE_PREFIX}${runtimeVersion}'
""", GRADLE_ENTERPRISE_PLUGIN_ID, GRADLE_ENTERPRISE_PLUGIN_CLASS_SIMPLE_NAME)

        builder.addPlugin("""
            println '$BUILD_SCAN_PLUGIN_APPLIED_MESSAGE'
        """, BUILD_SCAN_PLUGIN_ID, BUILD_SCAN_PLUGIN_CLASS_SIMPLE_NAME)

        builder.publishAs("com.gradle:gradle-enterprise-gradle-plugin:${artifactVersion}", mavenRepo, pluginBuildExecuter)
    }

    void assertDisabled(String output, boolean disabled) {
        assert output.contains("buildScan.disabled: $disabled")
    }

    void assertEnabled(String output, boolean enabled) {
        assert output.contains("buildScan.enabled: $enabled")
    }

    void assertUnsupportedMessage(String output, String unsupported) {
        assert output.contains("buildScan.unsupportedMessage: $unsupported")
    }

    BuildScanConfig.Attributes attributes(String output) {
        def all = allAttributes(output)
        all.empty ? null : all.first()
    }

    List<BuildScanConfig.Attributes> allAttributes(String output) {
        output.findAll("buildScan\\.attributes: \\{(.+)\\}\\\n") {
            it[1]
        }.collect {
            def map = new JsonSlurper().parseText("{" + it + "}")
            new BuildScanConfig.Attributes() {
                @Override
                boolean isRootProjectHasVcsMappings() {
                    return false
                }

                @Override
                boolean isTaskExecutingBuild() {
                    return map.taskExecutingBuild
                }
            }
        }
    }

    void issuedNoPluginWarning(String output) {
        assert output.contains(PLUGIN_NOT_APPLIED_MSG)
    }

    void didNotIssuedNoPluginWarning(String output) {
        assert !output.contains(PLUGIN_NOT_APPLIED_MSG)
    }

    void issuedNoPluginWarningCount(String output, int count) {
        assert output.count(PLUGIN_NOT_APPLIED_MSG) == count
    }

}
