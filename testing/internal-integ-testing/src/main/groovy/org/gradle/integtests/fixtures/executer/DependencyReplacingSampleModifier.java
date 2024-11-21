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

package org.gradle.integtests.fixtures.executer;

import com.google.common.io.Files;
import org.gradle.exemplar.model.Sample;
import org.gradle.exemplar.test.runner.SampleModifier;
import org.gradle.integtests.fixtures.versions.PublishedVersionDeterminer;
import org.gradle.util.GradleVersion;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.walk;

/**
 * This forces the samples to use the latest nightly build of the TAPI during test execution.
 * This allows us to more conveniently adopt sample code to the latest version of the TAPI
 */
public class DependencyReplacingSampleModifier implements SampleModifier {

    @Override
    public Sample modify(Sample sample) {
        listBuildScripts(sample.getProjectDir())
            .forEach(DependencyReplacingSampleModifier::replaceDependencies);
        return sample;
    }

    private static Stream<File> listBuildScripts(File projectDir) {
        try {
            return walk(projectDir.toPath())
                .map(f -> f.toFile())
                .filter(f -> f.getName().endsWith(".gradle") || f.getName().endsWith(".gradle.kts"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void replaceDependencies(File scriptFile) {
        // Currently, we only want to replace the Tooling API dependency to the latest released snapshot.
        // A better approach would be to target the under-development distribution,
        // but that takes significant refactorings (e.g., sharing fixtures from the TAPI cross-version tests).
        try {
            String content = Files.asCharSource(scriptFile, UTF_8).read();

            Optional<String> replacement = getReplacementString(content);
            if (replacement.isPresent()) {
                Files.asCharSink(scriptFile, UTF_8).write(replacement.get());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static Optional<String> getReplacementString(String content) {
        Matcher matcher = toolingApiPattern.matcher(content);

        // Check if a match is found
        if (matcher.find()) {
            // Extract the version from the matched string
            String existingVersion = matcher.group(1);
            GradleVersion versionInScript = GradleVersion.version(existingVersion);
            if (versionInScript.compareTo(latestReleasedVersion) > 0) {
                String replacement = matcher.replaceAll("org.gradle:gradle-tooling-api:" + latestNightlyVersion);
                replacement = replacement.replace("libs-releases", "libs-snapshots");
                return Optional.of(replacement);
            }
        }
        return Optional.empty();
    }

    // Pattern to match the tooling API dependency
    static Pattern toolingApiPattern = Pattern.compile("org\\.gradle:gradle-tooling-api:(\\d+(\\.\\d+)*)(-[0-9A-Za-z\\.\\+]+)?");

    // Placeholder for the latest versions
    static String latestNightlyVersion = PublishedVersionDeterminer.getLatestNightlyVersion();
    static GradleVersion latestReleasedVersion = GradleVersion.version(PublishedVersionDeterminer.getLatestReleasedVersion());

}
