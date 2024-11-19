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

package org.gradle.smoketests

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache
import org.gradle.util.GradleVersion
import org.gradle.util.internal.VersionNumber

import static org.gradle.api.internal.DocumentationRegistry.BASE_URL
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

@UnsupportedWithConfigurationCache(
    because = "The Gretty plugin does not support configuration caching"
)
class GrettySmokeTest extends AbstractPluginValidatingSmokeTest {

    def 'run Jetty with Gretty #grettyConfig.version'() {
        given:
        def grettyVersion = VersionNumber.parse(grettyConfig.version)
        useSample('gretty-example')
        buildFile << """
            plugins {
                id "war"
                id "org.gretty" version "${grettyVersion}"
            }

            ${mavenCentralRepository()}

            dependencies {
                implementation group: 'log4j', name: 'log4j', version: '1.2.15', ext: 'jar'
            }

            gretty {
                contextPath = 'quickstart'

                httpPort = new ServerSocket(0).withCloseable { socket -> socket.getLocalPort() }
                integrationTestTask = 'checkContainerUp'
                servletContainer = '${grettyConfig.servletContainer}'
            }

            task checkContainerUp {
                doLast {
                    URL url = new URL("http://localhost:\${gretty.httpPort}/quickstart")
                    assert url.text.contains('hello Gradle')
                }
            }
        """

        when:
        def result = runner('checkContainerUp')
            .expectDeprecationWarning(
                "The org.gradle.api.plugins.WarPluginConvention type has been deprecated. This is scheduled to be removed in Gradle 9.0. Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_8.html#war_convention_deprecation",
                "https://github.com/gretty-gradle-plugin/gretty/issues/266")
            .expectDeprecationWarningIf(
                grettyVersion < VersionNumber.parse("4.1.0"),
                "The org.gradle.util.VersionNumber type has been deprecated. This is scheduled to be removed in Gradle 9.0. Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_8.html#org_gradle_util_reports_deprecations",
                "https://github.com/gretty-gradle-plugin/gretty/issues/297"
            )
            .expectDeprecationWarning(
                "The Project.javaexec(Closure) method has been deprecated. " +
                    "This is scheduled to be removed in Gradle 9.0. " +
                    "Use ExecOperations.javaexec(Action) or ProviderFactory.javaexec(Action) instead. " +
                    "Consult the upgrading guide for further information: ${BASE_URL}/userguide/upgrading_version_8.html#deprecated_project_exec",
                "https://github.com/gretty-gradle-plugin/gretty/issues/312"
            )
            .expectDeprecationWarning(
                "Invocation of Task.project at execution time has been deprecated. " +
                    "This will fail with an error in Gradle 9.0. " +
                    "Consult the upgrading guide for further information: ${BASE_URL}/userguide/upgrading_version_7.html#task_project",
                "https://github.com/gretty-gradle-plugin/gretty/issues/313"
            )
            .build()

        then:
        result.task(':checkContainerUp').outcome == SUCCESS

        where:
        grettyConfig << grettyConfigForCurrentJavaVersion()
    }

    @Override
    Map<String, Versions> getPluginsToValidate() {
        [
            'org.gretty': Versions.of(grettyConfigForCurrentJavaVersion().collect { it.version } as String[])
        ]
    }

    static def grettyConfigForCurrentJavaVersion() {
        TestedVersions.gretty.findAll {
            JavaVersion.current().isCompatibleWith(it.javaMinVersion as JavaVersion) &&
                (it.javaMaxVersion == null || JavaVersion.current() <= JavaVersion.toVersion(it.javaMaxVersion))
        }
    }
}
