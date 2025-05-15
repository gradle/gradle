/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.docs.sample;

import org.apache.commons.io.FileUtils;
import org.gradle.exemplar.executor.CliCommandExecutor;
import org.gradle.exemplar.executor.CommandExecutionResult;
import org.gradle.exemplar.executor.CommandExecutor;
import org.gradle.exemplar.executor.ExecutionMetadata;
import org.gradle.exemplar.loader.SamplesDiscovery;
import org.gradle.exemplar.model.Command;
import org.gradle.exemplar.model.Sample;
import org.junit.Assert;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.gradle.exemplar.test.runner.SamplesRunner.SAFE_SYSTEM_PROPERTIES;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

public class DummyTest {
    @TempDir
    File tmpDir;

    private Sample initSample(final Sample sampleIn) throws IOException {
        File tmpProjectDir = new File(tmpDir, sampleIn.getId());
        tmpProjectDir.mkdirs();
        FileUtils.copyDirectory(sampleIn.getProjectDir(), tmpProjectDir);
        Sample sample = new Sample(sampleIn.getId(), tmpProjectDir, sampleIn.getCommands());
        return sample;
    }

    @TestFactory
    Stream<DynamicTest> dynamicTestsFromCollection() {
        List<Sample> samples = SamplesDiscovery.externalSamples(new File("src/snippets/artifacts"));
        return samples.stream().map(s -> dynamicTest(s.getId(), () -> {
            final Sample testSpecificSample = initSample(s);
            File baseWorkingDir = testSpecificSample.getProjectDir();

            // Execute and verify each command
            for (Command command : testSpecificSample.getCommands()) {
                File workingDir = baseWorkingDir;

                if (command.getExecutionSubdirectory() != null) {
                    workingDir = new File(workingDir, command.getExecutionSubdirectory());
                }

                // This should be some kind of plugable executor rather than hard-coded here
                if (command.getExecutable().equals("cd")) {
                    baseWorkingDir = new File(baseWorkingDir, command.getArgs().get(0)).getCanonicalFile();
                    continue;
                }

                CommandExecutionResult result = execute(getExecutionMetadata(testSpecificSample.getProjectDir()), workingDir, command);

                if (result.getExitCode() != 0 && !command.isExpectFailure()) {
                    Assert.fail(String.format("Expected sample invocation to succeed but it failed.%nCommand was: '%s %s'%nWorking directory: '%s'%n[BEGIN OUTPUT]%n%s%n[END OUTPUT]%n",
                        command.getExecutable(), s.getId(), workingDir.getAbsolutePath(), result.getOutput()));
                } else if (result.getExitCode() == 0 && command.isExpectFailure()) {
                    Assert.fail(String.format("Expected sample invocation to fail but it succeeded.%nCommand was: '%s %s'%nWorking directory: '%s'%n[BEGIN OUTPUT]%n%s%n[END OUTPUT]%n",
                        command.getExecutable(), s.getId(), workingDir.getAbsolutePath(), result.getOutput()));
                }
            }
        }));
    }


    private CommandExecutionResult execute(ExecutionMetadata executionMetadata, File workingDir, Command command) {
        return selectExecutor(executionMetadata, workingDir, command).execute(command, executionMetadata);
    }

    /**
     * Allows a subclass to provide a custom {@link CommandExecutor}.
     */
    protected CommandExecutor selectExecutor(ExecutionMetadata executionMetadata, File workingDir, Command command) {
        return new CliCommandExecutor(workingDir);
    }

    private ExecutionMetadata getExecutionMetadata(final File tempSampleOutputDir) {
        Map<String, String> systemProperties = new HashMap<>();
        for (String systemPropertyKey : SAFE_SYSTEM_PROPERTIES) {
            systemProperties.put(systemPropertyKey, System.getProperty(systemPropertyKey));
        }

        return new ExecutionMetadata(tempSampleOutputDir, systemProperties);
    }
}
