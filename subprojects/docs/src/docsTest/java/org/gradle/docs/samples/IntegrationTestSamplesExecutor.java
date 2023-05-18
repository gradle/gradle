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

package org.gradle.docs.samples;

import org.gradle.api.UncheckedIOException;
import org.gradle.api.logging.configuration.WarningMode;
import org.gradle.exemplar.executor.CommandExecutor;
import org.gradle.integtests.fixtures.AvailableJavaHomes;
import org.gradle.integtests.fixtures.executer.ExecutionFailure;
import org.gradle.integtests.fixtures.executer.ExecutionResult;
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter;
import org.gradle.integtests.fixtures.executer.GradleDistribution;
import org.gradle.integtests.fixtures.executer.GradleExecuter;
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext;
import org.gradle.integtests.fixtures.executer.UnderDevelopmentGradleDistribution;
import org.gradle.integtests.fixtures.executer.UnexpectedBuildFailure;
import org.gradle.internal.jvm.Jvm;
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toMap;

class IntegrationTestSamplesExecutor extends CommandExecutor {

    private static final String CHECK_LOADING_FROM_CONFIGURATION_CACHE = "org.gradle.integtest.samples.checkLoadingFromConfigurationCache";

    private static final String WARNING_MODE_FLAG_PREFIX = "--warning-mode=";

    private static final String NO_STACKTRACE_CHECK = "-Dorg.gradle.sampletest.noStackTraceCheck=true";

    private static final String SAMPLE_ENV_PREFIX = "-Dorg.gradle.sampletest.env.";

    private final File workingDir;
    private final boolean expectFailure;
    private final GradleExecuter gradle;

    IntegrationTestSamplesExecutor(File workingDir, boolean expectFailure) {
        this.workingDir = workingDir;
        this.expectFailure = expectFailure;
        GradleDistribution distribution = new UnderDevelopmentGradleDistribution(IntegrationTestBuildContext.INSTANCE);
        this.gradle = new GradleContextualExecuter(distribution, new TestNameTestDirectoryProvider(IntegrationTestSamplesExecutor.class), IntegrationTestBuildContext.INSTANCE);
    }

    @Override
    protected int run(String executable, List<String> args, List<String> flags, OutputStream outputStream) {
        try {
            GradleExecuter executer = createExecuter(args, flags);
            if (expectFailure) {
                // TODO(mlopatkin) sometimes it is still possible to save the configuration cache state in the event of failure.
                //  We need to figure out how to separate expected failure from the CC store failure.
                ExecutionFailure result = executer.runWithFailure();
                outputStream.write((result.getOutput() + result.getError()).getBytes());
            } else {
                ExecutionResult result = executer.run();
                outputStream.write(result.getOutput().getBytes());

                if (shouldCheckLoadingFromConfigurationCache()) {
                    rerunReusingStoredConfigurationCacheEntry(args, flags);
                }
            }
            return expectFailure ? 1 : 0;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void rerunReusingStoredConfigurationCacheEntry(List<String> args, List<String> flags) {
        // Rerun tasks. If the task is up-to-date, then its actions aren't executed.
        // This might hide issues with Groovy closures, as the methods referenced inside the closure are resolved dynamically.
        try {
            createExecuter(args, flags).withArgument("--rerun-tasks").run();
        } catch (UnexpectedBuildFailure e) {
            UnexpectedBuildFailure tweakedException = new UnexpectedBuildFailure("Failed to rerun the build when reusing the configuration cache entry.\n" + e.getMessage());
            tweakedException.setStackTrace(e.getStackTrace());
            throw tweakedException;
        }
    }

    private GradleExecuter createExecuter(List<String> args, List<String> flags) {
        WarningMode warningMode = flags.stream()
            .filter(it -> it.startsWith(WARNING_MODE_FLAG_PREFIX))
            .map(it -> WarningMode.valueOf(capitalize(it.replace(WARNING_MODE_FLAG_PREFIX, "").toLowerCase())))
            .findFirst().orElse(WarningMode.Fail);
        List<String> filteredFlags = flags.stream()
            .filter(it -> !it.startsWith(WARNING_MODE_FLAG_PREFIX) && !it.equals(NO_STACKTRACE_CHECK) && !it.startsWith(SAMPLE_ENV_PREFIX))
            .collect(toCollection(ArrayList::new));
        filteredFlags.add(getAvailableJdksFlag());
        GradleExecuter executer = gradle.inDirectory(workingDir).ignoreMissingSettingsFile()
            .noDeprecationChecks()
            .withWarningMode(warningMode)
            .withToolchainDetectionEnabled()
            .withArguments(filteredFlags)
            .withTasks(args);

        if (flags.stream().anyMatch(NO_STACKTRACE_CHECK::equals)) {
            executer.withStackTraceChecksDisabled();
        }

        Map<String, String> env = flags.stream()
            .filter(it -> it.startsWith(SAMPLE_ENV_PREFIX))
            .map(it -> it.replace(SAMPLE_ENV_PREFIX, "").split("="))
            .filter(it -> it.length == 2)
            .collect(toMap(it -> it[0], it -> it[1]));
        if (!env.isEmpty()) {
            executer.withEnvironmentVars(env);
        }
        return executer;
    }

    private String getAvailableJdksFlag() {
        String allJdkPaths = AvailableJavaHomes.getAvailableJvms().stream()
            .map(Jvm::getJavaHome)
            .map(File::getAbsolutePath)
            .collect(Collectors.joining(","));
        return "-Porg.gradle.java.installations.paths=" + allJdkPaths;
    }

    private static String capitalize(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    private static boolean shouldCheckLoadingFromConfigurationCache() {
        return Boolean.parseBoolean(System.getProperty(CHECK_LOADING_FROM_CONFIGURATION_CACHE));
    }
}
