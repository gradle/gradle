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

import org.gradle.exemplar.executor.CommandExecutor;
import org.gradle.exemplar.executor.ExecutionMetadata;
import org.gradle.exemplar.model.Command;
import org.gradle.exemplar.model.Sample;
import org.gradle.exemplar.test.runner.SamplesRunner;
import org.gradle.integtests.fixtures.mirror.SetMirrorsSampleModifier;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.model.InitializationError;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class IntegrationTestSamplesRunner extends SamplesRunner {
    private static final String SAMPLES_DIR_PROPERTY = "integTest.samplesdir";

    public IntegrationTestSamplesRunner(Class<?> testClass) throws InitializationError {
        super(testClass);
    }

    private static final Pattern OWN_INIT_SCRIPT = Pattern.compile("(?:--init-script|-I)(?:=|\\s+)(\\S+)");

    @Override
    protected CommandExecutor selectExecutor(ExecutionMetadata executionMetadata, File workingDir, Command command) {
        return new IntegrationTestSamplesExecutor(workingDir, command.isExpectFailure());
    }

    @Override
    protected void runChild(Sample sample, RunNotifier notifier) {
        if (SetMirrorsSampleModifier.isMavenCentralMirrored() && (ownInitScriptResolvesFromMavenCentral(sample) || buildSrcResolvesFromMavenCentral(sample) || launchesTestKitBuilds(sample))) {
            // No mirror reaches another init script's classpath, buildSrc on this Gradle line, or the sample's own TestKit builds.
            notifier.fireTestIgnored(describeChild(sample));
            return;
        }
        super.runChild(sample, notifier);
    }

    private static boolean launchesTestKitBuilds(Sample sample) {
        return anyFileContains(sample, ".*[\\\\/]src[\\\\/][A-Za-z]*[Tt]est[\\\\/].*\\.(groovy|kt|java)", "GradleRunner");
    }

    private static boolean buildSrcResolvesFromMavenCentral(Sample sample) {
        return anyFileContains(sample, ".*[\\\\/]buildSrc[\\\\/](build|settings)\\.gradle(\\.kts)?", "mavenCentral()");
    }

    private static boolean anyFileContains(Sample sample, String pathPattern, String text) {
        try (java.util.stream.Stream<java.nio.file.Path> files = Files.walk(sample.getProjectDir().toPath())) {
            return files.filter(Files::isRegularFile)
                .filter(f -> f.toString().matches(pathPattern))
                .anyMatch(f -> {
                    try {
                        return new String(Files.readAllBytes(f), StandardCharsets.UTF_8).contains(text);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean ownInitScriptResolvesFromMavenCentral(Sample sample) {
        for (Command command : sample.getCommands()) {
            List<String> tokens = new ArrayList<>(command.getArgs());
            tokens.addAll(command.getFlags());
            Matcher matcher = OWN_INIT_SCRIPT.matcher(String.join(" ", tokens));
            while (matcher.find()) {
                File script = new File(sample.getProjectDir(), matcher.group(1));
                try {
                    if (script.isFile() && new String(Files.readAllBytes(script.toPath()), StandardCharsets.UTF_8).contains("mavenCentral()")) {
                        return true;
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return false;
    }

    @Nullable
    @Override
    protected File getImplicitSamplesRootDir() {
        String samplesDir = System.getProperty(SAMPLES_DIR_PROPERTY);
        if (samplesDir == null) {
            throw new IllegalStateException(String.format("'%s' property is required", SAMPLES_DIR_PROPERTY));
        }
        return Paths.get(samplesDir).toFile();
    }
}
