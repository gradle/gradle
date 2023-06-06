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

package org.gradle.smoketests

import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.internal.enterprise.core.GradleEnterprisePluginManager
import org.gradle.testkit.runner.BuildResult
import org.gradle.util.GradleVersion
import org.gradle.util.internal.VersionNumber
import org.junit.Assume
import spock.lang.IgnoreIf

// https://plugins.gradle.org/plugin/com.gradle.enterprise
class BuildScanPluginSmokeTest extends AbstractSmokeTest {

    private static final List<String> UNSUPPORTED = [
        "2.4.2",
        "2.4.1",
        "2.4",
        "2.3",
        "2.2.1",
        "2.2",
        "2.1",
        "2.0.2",
        "2.0.1",
        "2.0",
        "1.16",
        "1.15",
        "1.14"
    ]

    private static final List<String> SUPPORTED = [
        "3.0",
        "3.1",
        "3.1.1",
        "3.2",
        "3.2.1",
        "3.3",
        "3.3.1",
        "3.3.2",
        "3.3.3",
        "3.3.4",
        "3.4",
        "3.4.1",
        "3.5",
        "3.5.1",
        "3.5.2",
        "3.6",
        "3.6.1",
        "3.6.2",
        "3.6.3",
        "3.6.4",
        "3.7",
        "3.7.1",
        "3.7.2",
        "3.8",
        "3.8.1",
        "3.9",
        "3.10",
        "3.10.1",
        "3.10.2",
        "3.10.3",
        // "3.11", This doesn't work on Java 8, so let's not test it.
        "3.11.1",
        "3.11.2",
        "3.11.3",
        "3.11.4",
        "3.12",
        "3.12.1",
        "3.12.2",
        "3.12.3",
        "3.12.4",
        "3.12.5",
        "3.12.6",
        "3.13",
        "3.13.1",
        "3.13.2",
        "3.13.3"
    ]

    private static final VersionNumber FIRST_VERSION_SUPPORTING_CONFIGURATION_CACHE = VersionNumber.parse("3.4")
    private static final VersionNumber FIRST_VERSION_SUPPORTING_GRADLE_8_CONFIGURATION_CACHE = VersionNumber.parse("3.12")
    private static final VersionNumber FIRST_VERSION_CALLING_BUILD_PATH = VersionNumber.parse("3.13.1")

    @IgnoreIf({ !GradleContextualExecuter.configCache })
    def "can use plugin #version with Gradle 8 configuration cache"() {
        given:
        def versionNumber = VersionNumber.parse(version)
        Assume.assumeFalse(versionNumber < FIRST_VERSION_SUPPORTING_GRADLE_8_CONFIGURATION_CACHE)

        when:
        usePluginVersion version

        then:
        scanRunner()
            .expectLegacyDeprecationWarningIf(versionNumber < FIRST_VERSION_CALLING_BUILD_PATH,
                "The BuildIdentifier.getName() method has been deprecated. " +
                    "This is scheduled to be removed in Gradle 9.0. " +
                    "Use getBuildPath() to get a unique identifier for the build. " +
                    "Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_8.html#build_identifier_name_and_current_deprecation"
            ).build().output.contains("Build scan written to")

        where:
        version << SUPPORTED
    }

    @IgnoreIf({ GradleContextualExecuter.configCache })
    def "can use plugin #version"() {
        given:
        def versionNumber = VersionNumber.parse(version)
        Assume.assumeFalse(versionNumber < FIRST_VERSION_SUPPORTING_CONFIGURATION_CACHE)

        when:
        usePluginVersion version

        then:
        scanRunner()
            .expectLegacyDeprecationWarningIf(versionNumber < FIRST_VERSION_CALLING_BUILD_PATH,
                "The BuildIdentifier.getName() method has been deprecated. " +
                    "This is scheduled to be removed in Gradle 9.0. " +
                    "Use getBuildPath() to get a unique identifier for the build. " +
                    "Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_8.html#build_identifier_name_and_current_deprecation"
            )
            .build().output.contains("Build scan written to")

        where:
        version << SUPPORTED
    }

    def "cannot use plugin #version"() {
        when:
        usePluginVersion version

        and:
        def output = runner("--stacktrace")
            .buildAndFail().output

        then:
        output.contains(GradleEnterprisePluginManager.OLD_SCAN_PLUGIN_VERSION_MESSAGE)

        where:
        version << UNSUPPORTED
    }

    @IgnoreIf({ GradleContextualExecuter.configCache })
    def "can inject plugin #version"() {
        def versionNumber = VersionNumber.parse(version)
        def initScript = "init-script.gradle"
        writeInjectionScript(initScript)

        // URL is not relevant as long as it's valid due to the `-Dscan.dump` parameter
        file("gradle.properties") << """
            systemProp.teamCityBuildScanPlugin.gradle-enterprise.plugin.version=$version
            systemProp.teamCityBuildScanPlugin.init-script.name=$initScript
            systemProp.teamCityBuildScanPlugin.gradle-enterprise.url=http://localhost:5086
        """.stripIndent()

        setupJavaProject()

        expect:
        scanRunner("--init-script", initScript)
            .expectLegacyDeprecationWarningIf(versionNumber < FIRST_VERSION_CALLING_BUILD_PATH,
                "The BuildIdentifier.getName() method has been deprecated. " +
                    "This is scheduled to be removed in Gradle 9.0. " +
                    "Use getBuildPath() to get a unique identifier for the build. " +
                    "Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_8.html#build_identifier_name_and_current_deprecation"
            )
            .build().output.contains("Build scan written to")

        where:
        // Current injection scripts support Gradle Enterprise plugin 3.3 and above
        version << SUPPORTED
            .findAll { VersionNumber.parse("3.3") <= VersionNumber.parse(it) }
    }

    BuildResult build(String... args) {
        scanRunner(args).build()
    }

    SmokeTestGradleRunner scanRunner(String... args) {
        runner("build", "-Dscan.dump", *args).forwardOutput()
    }

    void usePluginVersion(String version) {
        def gradleEnterprisePlugin = VersionNumber.parse(version) >= VersionNumber.parse("3.0")
        if (gradleEnterprisePlugin) {
            settingsFile << """
                plugins {
                    id "com.gradle.enterprise" version "$version"
                }

                gradleEnterprise {
                    buildScan {
                        termsOfServiceUrl = 'https://gradle.com/terms-of-service'
                        termsOfServiceAgree = 'yes'
                    }
                }
            """
        } else {
            buildFile << """
                plugins {
                    id "com.gradle.build-scan" version "$version"
                }

                buildScan {
                    termsOfServiceUrl = 'https://gradle.com/terms-of-service'
                    termsOfServiceAgree = 'yes'
                }
            """
        }

        setupJavaProject()
    }

    private setupJavaProject() {
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}

            dependencies {
                testImplementation 'junit:junit:4.13'
            }
        """

        file("src/main/java/MySource.java") << """
            public class MySource {
                public static boolean isTrue() { return true; }
            }
        """

        file("src/test/java/MyTest.java") << """
            import org.junit.*;

            public class MyTest {
               @Test
               public void test() {
                  Assert.assertTrue(MySource.isTrue());
               }
            }
        """
    }

    private writeInjectionScript(String initScriptFile) {
        // Should be kept in sync with the external injection script:
        // https://github.com/etiennestuder/teamcity-build-scan-plugin/blob/1b69acdd97b2d37c3b34d9c29787626f7b80ecbd/agent/src/main/resources/build-scan-init.gradle
        file(initScriptFile) << '''
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

    def requestedInitScriptName = getInputParam('teamCityBuildScanPlugin.init-script.name')
    def initScriptName = buildscript.sourceFile.name
    if (requestedInitScriptName != initScriptName) {
        return
    }

    def pluginRepositoryUrl = getInputParam('teamCityBuildScanPlugin.gradle.plugin-repository.url')
    def gePluginVersion = getInputParam('teamCityBuildScanPlugin.gradle-enterprise.plugin.version')
    def ccudPluginVersion = getInputParam('teamCityBuildScanPlugin.ccud.plugin.version')

    def atLeastGradle5 = GradleVersion.current() >= GradleVersion.version('5.0')
    def atLeastGradle4 = GradleVersion.current() >= GradleVersion.version('4.0')

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

def requestedInitScriptName = getInputParam('teamCityBuildScanPlugin.init-script.name')
def initScriptName = buildscript.sourceFile.name
if (requestedInitScriptName != initScriptName) {
    logger.quiet("Ignoring init script '${initScriptName}' as requested name '${requestedInitScriptName}' does not match")
    return
}

def geUrl = getInputParam('teamCityBuildScanPlugin.gradle-enterprise.url')
def geAllowUntrustedServer = Boolean.parseBoolean(getInputParam('teamCityBuildScanPlugin.gradle-enterprise.allow-untrusted-server'))
def geEnforceUrl = Boolean.parseBoolean(getInputParam('teamCityBuildScanPlugin.gradle-enterprise.enforce-url'))
def gePluginVersion = getInputParam('teamCityBuildScanPlugin.gradle-enterprise.plugin.version')
def ccudPluginVersion = getInputParam('teamCityBuildScanPlugin.ccud.plugin.version')

def atLeastGradle4 = GradleVersion.current() >= GradleVersion.version('4.0')

// finish early if configuration parameters passed in via system properties are not valid/supported
if (ccudPluginVersion && isNotAtLeast(ccudPluginVersion, '1.7')) {
    logger.warn("Common Custom User Data Gradle plugin must be at least 1.7. Configured version is $ccudPluginVersion.")
    return
}

// log via helper class to be Configuration Cache compatible when logging is required from callback function
def buildScanLifeCycleLogger = new BuildScanLifeCycleLogger(logger)

// send a message to the server that the build has started
buildScanLifeCycleLogger.log('BUILD_STARTED')

// define a buildScanPublished listener that captures the build scan URL and sends it in a message to the server
def buildScanPublishedAction = { def buildScan ->
    if (buildScan.metaClass.respondsTo(buildScan, 'buildScanPublished', Action)) {
        buildScan.buildScanPublished { scan ->
            buildScanLifeCycleLogger.log("BUILD_SCAN_URL:${scan.buildScanUri.toString()}")
        }
    }
}

// register buildScanPublished listener and optionally apply the GE / Build Scan plugin
if (GradleVersion.current() < GradleVersion.version('6.0')) {
    //noinspection GroovyAssignabilityCheck
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
                    buildScan.value 'CI auto injection', 'TeamCity'
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

        pluginManager.withPlugin(BUILD_SCAN_PLUGIN_ID) {
            buildScanPublishedAction(buildScan)
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
                    ext.buildScan.value 'CI auto injection', 'TeamCity'
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

        extensionsWithPublicType(settings, GRADLE_ENTERPRISE_EXTENSION_CLASS).collect { settings[it.name] }.each { ext ->
            buildScanPublishedAction(ext.buildScan)
        }
    }
}

static def extensionsWithPublicType(def container, String publicType) {
    container.extensions.extensionsSchema.elements.findAll { it.publicType.concreteClass.name == publicType }
}

static boolean isNotAtLeast(String versionUnderTest, String referenceVersion) {
    GradleVersion.version(versionUnderTest) < GradleVersion.version(referenceVersion)
}

class BuildScanLifeCycleLogger {

    def logger

    BuildScanLifeCycleLogger(logger) {
        this.logger = logger
    }

    void log(String message) {
        logger.quiet(generateBuildScanLifeCycleMessage(message))
    }

    private static String generateBuildScanLifeCycleMessage(def attribute) {
        return "##teamcity[nu.studer.teamcity.buildscan.buildScanLifeCycle '${escape(attribute as String)}']" as String
    }

    private static String escape(String value) {
        return value?.toCharArray()?.collect { ch -> escapeChar(ch) }?.join()
    }

    private static String escapeChar(char ch) {
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

}
'''
    }
}
