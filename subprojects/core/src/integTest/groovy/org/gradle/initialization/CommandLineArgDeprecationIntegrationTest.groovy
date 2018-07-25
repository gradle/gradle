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

package org.gradle.initialization

import org.gradle.api.logging.configuration.WarningMode
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.tooling.fixture.ToolingApi
import org.gradle.internal.featurelifecycle.LoggingDeprecatedFeatureHandler
import spock.lang.Unroll

class CommandLineArgDeprecationIntegrationTest extends AbstractIntegrationSpec {

    private static final String RECOMPILE_SCRIPTS_MESSAGE = '--recompile-scripts has been deprecated. This is scheduled to be removed in Gradle'
    private static final String NO_REBUILD_MESSAGE = '--no-rebuild/-a has been deprecated. This is scheduled to be removed in Gradle'

    @Unroll
    def "deprecation warning appears when using #deprecatedArgs"() {
        when:
        executer.expectDeprecationWarning()
        args(deprecatedArgs)

        then:
        succeeds('help')
        outputContains(message)

        where:
        issue                                          | deprecatedArgs        | message
        'https://github.com/gradle/gradle/issues/1425' | '--recompile-scripts' | RECOMPILE_SCRIPTS_MESSAGE
        'https://github.com/gradle/gradle/issues/3077' | '--no-rebuild'        | NO_REBUILD_MESSAGE
        'https://github.com/gradle/gradle/issues/3077' | '-a'                  | NO_REBUILD_MESSAGE
    }

    @Unroll
    def "deprecation warning appears when using #deprecatedArgs and warning mode #warningsType in Tooling API"() {
        given:
        ToolingApi toolingApi = new ToolingApi(distribution, temporaryFolder)
        toolingApi.requireIsolatedDaemons()

        when:
        def stdOut = new ByteArrayOutputStream()
        def args = [deprecatedArgs]
        if (warningsType != null) {
            args.add("--warning-mode=" + warningsType.toString().toLowerCase(Locale.ENGLISH))
        }
        toolingApi.withConnection { connection -> connection.newBuild().withArguments(args).forTasks('help').setStandardOutput(stdOut).run() }

        then:
        warningCountInConsole == stdOut.toString().count(message)
        warningCountInSummary == stdOut.toString().count(LoggingDeprecatedFeatureHandler.WARNING_SUMMARY)

        where:
        issue                                          | deprecatedArgs        | warningsType     | warningCountInConsole | warningCountInSummary | message
        'https://github.com/gradle/gradle/issues/1425' | '--recompile-scripts' | WarningMode.All  | 1                     | 0                     | RECOMPILE_SCRIPTS_MESSAGE
        'https://github.com/gradle/gradle/issues/3077' | '--no-rebuild'        | WarningMode.All  | 1                     | 0                     | NO_REBUILD_MESSAGE
        'https://github.com/gradle/gradle/issues/3077' | '-a'                  | WarningMode.All  | 1                     | 0                     | NO_REBUILD_MESSAGE
        'https://github.com/gradle/gradle/issues/3077' | '-a'                  | WarningMode.All  | 1                     | 0                     | NO_REBUILD_MESSAGE
        'https://github.com/gradle/gradle/issues/3077' | '-a'                  | null             | 0                     | 1                     | NO_REBUILD_MESSAGE
        'https://github.com/gradle/gradle/issues/3077' | '-a'                  | WarningMode.None | 0                     | 0                     | NO_REBUILD_MESSAGE
    }
}
