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

package org.gradle.internal.scan.config

import groovy.json.JsonSlurper
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.plugin.management.internal.autoapply.AutoAppliedBuildScanPlugin
import org.gradle.test.fixtures.plugin.PluginBuilder
import spock.lang.Unroll

import static org.gradle.util.TextUtil.normaliseFileSeparators

@Unroll
class BuildScanConfigIntegrationTest extends AbstractIntegrationSpec {

    private static final String PLUGIN_NOT_APPLIED_MSG = """Build scan cannot be created because the build scan plugin was not applied.
For more information on how to apply the build scan plugin, please visit https://gradle.com/scans/help/gradle-cli."""

    boolean collect = true
    String pluginVersionNumber = "2.0"

    def setup() {
        publishDummyBuildScanPlugin(AutoAppliedBuildScanPlugin.VERSION)

        executer.beforeExecute {
            if (collect) {
                buildScript """
                    def c = services.get(${BuildScanConfigProvider.name}).collect([getVersion: { "$pluginVersionNumber" }] as $BuildScanPluginMetadata.name) 
                    println "buildScan.enabled: " + c.enabled 
                    println "buildScan.disabled: " + c.disabled 
                    println "buildScan.unsupportedMessage: " + c.unsupportedMessage
                    println "buildScan.attributes: " + groovy.json.JsonOutput.toJson(c.attributes) 
                """
            }

            buildFile << """    
                def pluginApplied = services.get(${BuildScanPluginApplied.name}).isBuildScanPluginApplied()
                println "buildScan plugin applied: " + pluginApplied
            """

            buildFile << "task t"
        }
    }

    private void publishDummyBuildScanPlugin(String version) {
        settingsFile << """
            pluginManagement {
                repositories {
                    maven { url '${mavenRepo.uri}' }
                }
            }
"""
        def builder = new PluginBuilder(testDirectory.file('plugin-' + version))
        builder.addPlugin("", "com.gradle.build-scan", "DummyBuildScanPlugin")
        builder.publishAs("com.gradle:build-scan-plugin:${version}", mavenRepo, executer)
    }

    def "enabled and disabled are false with no flags"() {
        when:
        succeeds "t"

        then:
        assertEnabled(false)
        assertDisabled(false)
    }

    def "enabled with --scan"() {
        when:
        succeeds "t", "--scan"

        then:
        assertEnabled(true)
        assertDisabled(false)
    }

    def "disabled with --no-scan"() {
        when:
        succeeds "t", "--no-scan"

        then:
        assertEnabled(false)
        assertDisabled(true)
    }

    def "not enabled with -Dscan"() {
        // build scan plugin will treat this as enabled
        when:
        succeeds "t", "-Dscan"

        then:
        assertEnabled(false)
        assertDisabled(false)
    }

    def "not disabled with -Dscan=false"() {
        when:
        succeeds "t", "-Dscan=false"

        then:
        assertEnabled(false)
        assertDisabled(false)
    }

    def "warns if scan requested but no scan plugin applied"() {
        given:
        collect = false

        when:
        succeeds "t", "--scan"

        then:
        issuedNoPluginWarning()
    }

    def "warns if scan requested by sys prop value #value but no scan plugin applied"() {
        given:
        collect = false

        when:
        succeeds "t", value == null ? "-Dscan" : "-Dscan=$value"

        then:
        issuedNoPluginWarning()

        where:
        value << [null, "", "true", "yes"]
    }

    def "does not warn if no scan requested but no scan plugin applied"() {
        given:
        collect = false

        when:
        succeeds "t", "--no-scan"

        then:
        !issuedNoPluginWarning()
    }

    def "fails if plugin is too old"() {
        given:
        pluginVersionNumber = "1.7.4"

        when:
        fails "t", "--scan"

        then:
        assertFailedVersionCheck()

        when:
        fails "t", "--no-scan"

        then:
        assertFailedVersionCheck()

        when:
        fails "t"

        then:
        assertFailedVersionCheck()
    }

    def "does not warn for each nested build if --scan used"() {
        given:
        collect = false
        file("buildSrc/build.gradle") << ""
        file("a/buildSrc/build.gradle") << ""
        file("a/build.gradle") << ""
        file("a/settings.gradle") << ""
        file("b/buildSrc/build.gradle") << ""
        file("b/build.gradle") << ""
        file("b/settings.gradle") << ""
        settingsFile << """
            includeBuild "a"
            includeBuild "b"
        """

        when:
        succeeds "--scan"

        then:
        output.count(PLUGIN_NOT_APPLIED_MSG) == 1
    }

    def "detects that the build scan plugin has been #description"() {
        given:
        collect = applied

        when:
        succeeds "t"

        then:
        output.contains("buildScan plugin applied: ${applied}")
        if (applied) {
            with(attributes()) {
                !isRootProjectHasVcsMappings()
            }
        }

        where:
        applied << [true, false]
        description = applied ? "applied" : "not applied"
    }

    def "fails when VCS mappings are being used and plugin is too old"() {
        given:
        pluginVersionNumber = "1.10"
        installVcsMappings()

        when:
        fails "t"

        then:
        failureCauseContains(BuildScanPluginCompatibility.UNSUPPORTED_VCS_MAPPINGS_MESSAGE)
    }

    def "conveys when VCS mappings are being used and plugin is not too old"() {
        given:
        pluginVersionNumber = "1.11"
        installVcsMappings()

        when:
        succeeds "t"

        then:
        assertUnsupportedMessage(null)
        attributes().rootProjectHasVcsMappings
    }

    def "can convey unsupported to plugin that supports it"() {
        given:
        pluginVersionNumber = "1.11"
        when:
        succeeds "t", "-D${BuildScanPluginCompatibility.UNSUPPORTED_TOGGLE}=true"

        then:
        assertUnsupportedMessage(BuildScanPluginCompatibility.UNSUPPORTED_TOGGLE_MESSAGE)
        attributes() != null
    }

    void installVcsMappings() {
        def mapped = file('repo/mapped')
        settingsFile.text = """
            import org.gradle.vcs.internal.DirectoryRepositorySpec
            sourceControl {
                vcsMappings {
                    withModule('external-source:artifact') {
                        from vcs(DirectoryRepositorySpec) {
                            sourceDir = file('${normaliseFileSeparators(mapped.absolutePath)}')
                        }
                    }
                }
            }

        """
    }

    void assertFailedVersionCheck() {
        failureCauseContains(BuildScanPluginCompatibility.UNSUPPORTED_PLUGIN_VERSION_MESSAGE)
    }

    void assertDisabled(boolean disabled) {
        assert output.contains("buildScan.disabled: $disabled")
    }

    void assertEnabled(boolean enabled) {
        assert output.contains("buildScan.enabled: $enabled")
    }

    void assertUnsupportedMessage(String unsupported) {
        assert output.contains("buildScan.unsupportedMessage: $unsupported")
    }

    BuildScanConfig.Attributes attributes() {
        def jsonBody = output.find( "buildScan\\.attributes: \\{(.+)\\}\\\n") {
            it[1]
        }

        if (jsonBody == null) {
            return null
        }

        def map = new JsonSlurper().parseText("{" + jsonBody + "}")
        new BuildScanConfig.Attributes() {
            @Override
            boolean isRootProjectHasVcsMappings() {
                return map.rootProjectHasVcsMappings
            }
        }
    }

    boolean issuedNoPluginWarning() {
        output.contains PLUGIN_NOT_APPLIED_MSG
    }

}
