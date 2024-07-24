/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.cc.impl

import org.gradle.initialization.StartParameterBuildOptions.ConfigurationCacheMaxProblemsOption
import org.gradle.initialization.StartParameterBuildOptions.ConfigurationCacheOption
import org.gradle.initialization.StartParameterBuildOptions.ConfigurationCacheParallelOption
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.internal.cc.impl.fixtures.AbstractConfigurationCacheOptInFeatureIntegrationTest
import org.intellij.lang.annotations.Language

abstract class AbstractConfigurationCacheIntegrationTest extends AbstractConfigurationCacheOptInFeatureIntegrationTest {

    static final String ENABLE_CLI_OPT = "--${ConfigurationCacheOption.LONG_OPTION}"
    static final String ENABLE_GRADLE_PROP = "${ConfigurationCacheOption.PROPERTY_NAME}=true"
    static final String ENABLE_SYS_PROP = "-D$ENABLE_GRADLE_PROP"

    static final String DISABLE_CLI_OPT = "--no-${ConfigurationCacheOption.LONG_OPTION}"
    static final String DISABLE_GRADLE_PROP = "${ConfigurationCacheOption.PROPERTY_NAME}=false"
    static final String DISABLE_SYS_PROP = "-D$DISABLE_GRADLE_PROP"
    // Should be provided if a link to the report is expected even if no errors were found
    static final String LOG_REPORT_LINK_AS_WARNING = "-Dorg.gradle.configuration-cache.internal.report-link-as-warning=true"

    static final String MAX_PROBLEMS_GRADLE_PROP = "${ConfigurationCacheMaxProblemsOption.PROPERTY_NAME}"
    static final String MAX_PROBLEMS_SYS_PROP = "-D$MAX_PROBLEMS_GRADLE_PROP"

    static final String ENABLE_PARALLEL_CACHE = "-D${ConfigurationCacheParallelOption.PROPERTY_NAME}=true"

    private static final String[] CLI_OPTIONS = [ENABLE_CLI_OPT, LOG_REPORT_LINK_AS_WARNING, ENABLE_PARALLEL_CACHE, "--no-problems-report"]

    void buildKotlinFile(@Language(value = "kotlin") String script) {
        buildKotlinFile << script
    }

    void withConfigurationCache(String... moreExecuterArgs) {
        executer.withArguments(ENABLE_CLI_OPT, LOG_REPORT_LINK_AS_WARNING, *moreExecuterArgs)
    }

    void withConfigurationCacheLenient(String... moreExecuterArgs) {
        executer.withArguments(ENABLE_CLI_OPT, LOG_REPORT_LINK_AS_WARNING, WARN_PROBLEMS_CLI_OPT, *moreExecuterArgs)
    }

    void configurationCacheRun(String... tasks) {
        run(*CLI_OPTIONS, *tasks)
    }

    void configurationCacheRunLenient(String... tasks) {
        run(WARN_PROBLEMS_CLI_OPT, *CLI_OPTIONS, *tasks)
    }

    void configurationCacheFails(String... tasks) {
        fails(*CLI_OPTIONS, *tasks)
    }

    protected void assertTestsExecuted(String testClass, String... testNames) {
        new DefaultTestExecutionResult(testDirectory)
            .testClass(testClass)
            .assertTestsExecuted(testNames)
    }

    protected static String removeVfsLogOutput(String normalizedOutput) {
        normalizedOutput
            .replaceAll(/Received \d+ file system events .*\n/, '')
            .replaceAll(/Spent \d+ ms processing file system events since last build\n/, '')
            .replaceAll(/Spent \d+ ms registering watches for file system events\n/, '')
            .replaceAll(/Virtual file system .*\n/, '')
    }
}
