/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.util.GradleVersion

// TODO: injection script license?
class GradleEnterpriseInjectionScriptIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        def gradeCurrent = GradleVersion.current()
        def gradle83 = GradleVersion.version("8.3")
        def gradleCurrentIf83 = gradeCurrent.baseVersion == gradle83 ? gradeCurrent : gradle83;

        file("init-script.gradle") << '''
import org.gradle.util.GradleVersion

// note that there is no mechanism to share code between the initscript{} block and the main script, so some logic is duplicated

// conditionally apply the GE / Build Scan plugin to the classpath so it can be applied to the build further down in this script
initscript {
    def isTopLevelBuild = !gradle.parent
    if (!isTopLevelBuild) {
        return
    }

    def getInputParam = { String name ->
        def envVarName = name.toUpperCase().replace('.', '_').replace('-', '_')
        return System.getProperty(name) ?: System.getenv(envVarName)
    }

    // finish early if injection is disabled
    def gradleInjectionEnabled = getInputParam("jenkinsGradlePlugin.gradle-enterprise.gradle-injection-enabled")
    if (gradleInjectionEnabled == "false") {
        return
    }

    def pluginRepositoryUrl = getInputParam('jenkinsGradlePlugin.gradle.plugin-repository.url')
    def gePluginVersion = getInputParam('jenkinsGradlePlugin.gradle-enterprise.plugin.version')
    def ccudPluginVersion = getInputParam('jenkinsGradlePlugin.ccud.plugin.version')
''' + """
    def atLeastGradle83 = GradleVersion.current() >= GradleVersion.version('${gradleCurrentIf83.version}')
""" + '''
    def atLeastGradle5 = GradleVersion.current() >= GradleVersion.version('5.0')
    def atLeastGradle4 = GradleVersion.current() >= GradleVersion.version('4.0')

    if (gePluginVersion && atLeastGradle83) {
        gePluginVersion = org.gradle.plugin.management.internal.KnownPluginVersions.getGradleEnterprisePluginSupportedVersion(gePluginVersion)
    }

    if (gePluginVersion || ccudPluginVersion && atLeastGradle4) {
        pluginRepositoryUrl = pluginRepositoryUrl ?: 'https://plugins.gradle.org/m2'
        logger.quiet("Gradle Enterprise plugins resolution: $pluginRepositoryUrl")

        repositories {
            maven { url pluginRepositoryUrl }
        }
    }

    dependencies {
        if (gePluginVersion) {
            classpath atLeastGradle5 ?
                "com.gradle:gradle-enterprise-gradle-plugin:$gePluginVersion" :
                "com.gradle:build-scan-plugin:1.16"
        }

        if (ccudPluginVersion && atLeastGradle4) {
            classpath "com.gradle:common-custom-user-data-gradle-plugin:$ccudPluginVersion"
        }
    }
}

def BUILD_SCAN_PLUGIN_ID = 'com.gradle.build-scan'
def BUILD_SCAN_PLUGIN_CLASS = 'com.gradle.scan.plugin.BuildScanPlugin'

def GRADLE_ENTERPRISE_PLUGIN_ID = 'com.gradle.enterprise'
def GRADLE_ENTERPRISE_PLUGIN_CLASS = 'com.gradle.enterprise.gradleplugin.GradleEnterprisePlugin'
def GRADLE_ENTERPRISE_EXTENSION_CLASS = 'com.gradle.enterprise.gradleplugin.GradleEnterpriseExtension'
def CI_AUTO_INJECTION_CUSTOM_VALUE_NAME = 'CI auto injection'
def CI_AUTO_INJECTION_CUSTOM_VALUE_VALUE = 'Jenkins'
def CCUD_PLUGIN_ID = 'com.gradle.common-custom-user-data-gradle-plugin'
def CCUD_PLUGIN_CLASS = 'com.gradle.CommonCustomUserDataGradlePlugin'

def isTopLevelBuild = !gradle.parent
if (!isTopLevelBuild) {
    return
}

def getInputParam = { String name ->
    def envVarName = name.toUpperCase().replace('.', '_').replace('-', '_')
    return System.getProperty(name) ?: System.getenv(envVarName)
}

// finish early if injection is disabled
def gradleInjectionEnabled = getInputParam("jenkinsGradlePlugin.gradle-enterprise.gradle-injection-enabled")
if (gradleInjectionEnabled == "false") {
    return
}

def geUrl = getInputParam('jenkinsGradlePlugin.gradle-enterprise.url')
def geAllowUntrustedServer = Boolean.parseBoolean(getInputParam('jenkinsGradlePlugin.gradle-enterprise.allow-untrusted-server'))
def geEnforceUrl = Boolean.parseBoolean(getInputParam('jenkinsGradlePlugin.gradle-enterprise.enforce-url'))
def gePluginVersion = getInputParam('jenkinsGradlePlugin.gradle-enterprise.plugin.version')
def ccudPluginVersion = getInputParam('jenkinsGradlePlugin.ccud.plugin.version')

def atLeastGradle83 = GradleVersion.current() >= GradleVersion.version('8.3')
def atLeastGradle4 = GradleVersion.current() >= GradleVersion.version('4.0')

// finish early if configuration parameters passed in via system properties are not valid/supported
if (ccudPluginVersion && isNotAtLeast(ccudPluginVersion, '1.7')) {
    logger.warn("Common Custom User Data Gradle plugin must be at least 1.7. Configured version is $ccudPluginVersion.")
    return
}

if (gePluginVersion && atLeastGradle83) {
    gePluginVersion = org.gradle.plugin.management.internal.KnownPluginVersions.getGradleEnterprisePluginSupportedVersion(gePluginVersion)
}

// register buildScanPublished listener and optionally apply the GE / Build Scan plugin
if (GradleVersion.current() < GradleVersion.version('6.0')) {
    rootProject {
        buildscript.configurations.getByName("classpath").incoming.afterResolve { ResolvableDependencies incoming ->
            def resolutionResult = incoming.resolutionResult

            if (gePluginVersion) {
                def scanPluginComponent = resolutionResult.allComponents.find {
                    it.moduleVersion.with { group == "com.gradle" && (name == "build-scan-plugin" || name == "gradle-enterprise-gradle-plugin") }
                }
                if (!scanPluginComponent) {
                    logger.quiet("Applying $BUILD_SCAN_PLUGIN_CLASS via init script")
                    logger.quiet("Connection to Gradle Enterprise: $geUrl, allowUntrustedServer: $geAllowUntrustedServer")
                    pluginManager.apply(initscript.classLoader.loadClass(BUILD_SCAN_PLUGIN_CLASS))
                    buildScan.server = geUrl
                    buildScan.allowUntrustedServer = geAllowUntrustedServer
                    buildScan.publishAlways()
                    if (buildScan.metaClass.respondsTo(buildScan, 'setUploadInBackground', Boolean)) buildScan.uploadInBackground = false  // uploadInBackground not available for build-scan-plugin 1.16
                    buildScan.value CI_AUTO_INJECTION_CUSTOM_VALUE_NAME, CI_AUTO_INJECTION_CUSTOM_VALUE_VALUE
                }

                if (geUrl && geEnforceUrl) {
                    pluginManager.withPlugin(BUILD_SCAN_PLUGIN_ID) {
                        afterEvaluate {
                            logger.quiet("Enforcing Gradle Enterprise: $geUrl, allowUntrustedServer: $geAllowUntrustedServer")
                            buildScan.server = geUrl
                            buildScan.allowUntrustedServer = geAllowUntrustedServer
                        }
                    }
                }
            }

            if (ccudPluginVersion && atLeastGradle4) {
                def ccudPluginComponent = resolutionResult.allComponents.find {
                    it.moduleVersion.with { group == "com.gradle" && name == "common-custom-user-data-gradle-plugin" }
                }
                if (!ccudPluginComponent) {
                    logger.quiet("Applying $CCUD_PLUGIN_CLASS via init script")
                    pluginManager.apply(initscript.classLoader.loadClass(CCUD_PLUGIN_CLASS))
                }
            }
        }
    }
} else {
    gradle.settingsEvaluated { settings ->
        if (gePluginVersion) {
            if (!settings.pluginManager.hasPlugin(GRADLE_ENTERPRISE_PLUGIN_ID)) {
                logger.quiet("Applying $GRADLE_ENTERPRISE_PLUGIN_CLASS via init script")
                logger.quiet("Connection to Gradle Enterprise: $geUrl, allowUntrustedServer: $geAllowUntrustedServer")
                settings.pluginManager.apply(initscript.classLoader.loadClass(GRADLE_ENTERPRISE_PLUGIN_CLASS))
                extensionsWithPublicType(settings, GRADLE_ENTERPRISE_EXTENSION_CLASS).collect { settings[it.name] }.each { ext ->
                    ext.server = geUrl
                    ext.allowUntrustedServer = geAllowUntrustedServer
                    ext.buildScan.publishAlways()
                    ext.buildScan.uploadInBackground = false
                    ext.buildScan.value CI_AUTO_INJECTION_CUSTOM_VALUE_NAME, CI_AUTO_INJECTION_CUSTOM_VALUE_VALUE
                }
            }

            if (geUrl && geEnforceUrl) {
                extensionsWithPublicType(settings, GRADLE_ENTERPRISE_EXTENSION_CLASS).collect { settings[it.name] }.each { ext ->
                    logger.quiet("Enforcing Gradle Enterprise: $geUrl, allowUntrustedServer: $geAllowUntrustedServer")
                    ext.server = geUrl
                    ext.allowUntrustedServer = geAllowUntrustedServer
                }
            }
        }

        if (ccudPluginVersion) {
            if (!settings.pluginManager.hasPlugin(CCUD_PLUGIN_ID)) {
                logger.quiet("Applying $CCUD_PLUGIN_CLASS via init script")
                settings.pluginManager.apply(initscript.classLoader.loadClass(CCUD_PLUGIN_CLASS))
            }
        }
    }
}

static def extensionsWithPublicType(def container, String publicType) {
    container.extensions.extensionsSchema.elements.findAll { it.publicType.concreteClass.name == publicType }
}

static String escape(String value) {
    return value?.toCharArray()?.collect { ch -> escapeChar(ch) }?.join()
}

static String escapeChar(char ch) {
    String escapeCharacter = "|"
    switch (ch) {
        case '\\n': return escapeCharacter + "n"
        case '\\r': return escapeCharacter + "r"
        case '|': return escapeCharacter + "|"
        case '\\'': return escapeCharacter + "\\'"
        case '[': return escapeCharacter + "["
        case ']': return escapeCharacter + "]"
        default: return ch < 128 ? ch as String : escapeCharacter + String.format("0x%04x", (int) ch)
    }
}

static boolean isNotAtLeast(String versionUnderTest, String referenceVersion) {
    GradleVersion.version(versionUnderTest) < GradleVersion.version(referenceVersion)
}
'''
    }

    def "scripts selects latest known supported version of the plugin"() {
        // URL is not relevant as long as it's valid due to the `-Dscan.dump` parameter
        file("gradle.properties") << """
            systemProp.jenkinsGradlePlugin.gradle-enterprise.plugin.version=$preferredVersion
            systemProp.jenkinsGradlePlugin.gradle-enterprise.url=http://localhost:5086
        """.stripIndent()

        when:
        run("-I", "init-script.gradle", "help", "-Dscan.dump")

        then:
        executedAndNotSkipped(":help")

        outputContains("Applying com.gradle.enterprise.gradleplugin.GradleEnterprisePlugin via init script")
        postBuildOutputContains("Build scan written to:")

        def gradleVersion = GradleVersion.current().version
        def buildScanPath = result.getPostBuildOutputLineThatContains("/build-scan-$gradleVersion-")
        buildScanPath.contains("/build-scan-$gradleVersion-$supportedVersion-")

        where:
        preferredVersion | supportedVersion
        '2.0'            | '3.13.2'
        '3.10'           | '3.10'
        '3.13.2'         | '3.13.2'
    }

}
