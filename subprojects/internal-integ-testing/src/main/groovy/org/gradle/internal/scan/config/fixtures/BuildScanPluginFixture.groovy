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

package org.gradle.internal.scan.config.fixtures

import groovy.json.JsonSlurper
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.internal.scan.config.BuildScanConfig
import org.gradle.internal.scan.config.BuildScanPluginApplied
import org.gradle.plugin.management.internal.autoapply.AutoAppliedBuildScanPlugin
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.maven.MavenFileRepository
import org.gradle.test.fixtures.plugin.PluginBuilder

import static org.gradle.test.fixtures.plugin.PluginBuilder.packageName

@SuppressWarnings("GrMethodMayBeStatic")
class BuildScanPluginFixture {

    private static final String PLUGIN_NOT_APPLIED_MSG = """Build scan cannot be created because the build scan plugin was not applied.
For more information on how to apply the build scan plugin, please visit https://gradle.com/scans/help/gradle-cli."""

    public static final String BUILD_SCAN_PLUGIN_ID = AutoAppliedBuildScanPlugin.ID.id
    public static final String PUBLISHING_BUILD_SCAN_MESSAGE_PREFIX = 'PUBLISHING BUILD SCAN v'
    public static final String DUMMY_BUILD_SCAN_PLUGIN_IMPL_CLASS = 'DummyBuildScanPlugin'
    public static final String FULLY_QUALIFIED_DUMMY_BUILD_SCAN_PLUGIN_IMPL_CLASS = "${packageName}.${DUMMY_BUILD_SCAN_PLUGIN_IMPL_CLASS}"

    private final TestFile projectDir
    private final MavenFileRepository mavenRepo
    private final GradleExecuter pluginBuildExecuter

    boolean collectConfig = true
    boolean logConfig
    boolean logApplied

    protected boolean added

    String runtimeVersion = AutoAppliedBuildScanPlugin.VERSION
    String artifactVersion = AutoAppliedBuildScanPlugin.VERSION

    BuildScanPluginFixture(TestFile projectDir, MavenFileRepository mavenRepo, GradleExecuter pluginBuildExecuter) {
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

    void publishDummyBuildScanPlugin(GradleExecuter executer) {
        executer.beforeExecute {
            publishDummyBuildScanPluginNow()
        }
    }

    void publishDummyBuildScanPluginNow() {
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
                def pluginApplied = services.get(${BuildScanPluginApplied.name}).isBuildScanPluginApplied()
                println "buildScan plugin applied: " + pluginApplied
            """
        }

        def builder = new PluginBuilder(projectDir.file('plugin-' + AutoAppliedBuildScanPlugin.ID.id))
        builder.addPlugin("""
            project.gradle.buildFinished {
                println '${PUBLISHING_BUILD_SCAN_MESSAGE_PREFIX}${runtimeVersion}'
            }
""", BUILD_SCAN_PLUGIN_ID, DUMMY_BUILD_SCAN_PLUGIN_IMPL_CLASS)

        builder.publishAs("com.gradle:build-scan-plugin:${artifactVersion}", mavenRepo, pluginBuildExecuter)
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
                    return map.rootProjectHasVcsMappings
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
