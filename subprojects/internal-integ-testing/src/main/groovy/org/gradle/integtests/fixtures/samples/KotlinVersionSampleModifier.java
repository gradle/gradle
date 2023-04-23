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

package org.gradle.integtests.fixtures.samples;

import org.gradle.exemplar.model.Command;
import org.gradle.exemplar.model.Sample;
import org.gradle.exemplar.test.runner.SampleModifier;
import org.gradle.util.internal.kotlin.KotlinDslVersion;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;

public class KotlinVersionSampleModifier implements SampleModifier {

    private static final String KOTLIN_VERSION_PLACEHOLDER = "@kotlinVersion@";
    private static final String HAS_KOTLIN_VERSION_PLACEHOLDER_FLAG = "hasKotlinVersionPlaceholder";

    @Override
    public Sample modify(Sample sample) {
        boolean hasKotlinVersionPlaceholder = sample.getCommands().stream()
            .anyMatch(command -> command.getFlags().contains(HAS_KOTLIN_VERSION_PLACEHOLDER_FLAG));
        if (!hasKotlinVersionPlaceholder) {
            return sample;
        }

        replaceKotlinVersionForEveryFile(sample.getProjectDir().toPath());
        List<Command> commands = sample.getCommands().stream()
            .map(KotlinVersionSampleModifier::removeReplaceKotlinVersionFlag)
            .collect(Collectors.toList());
        return new Sample(sample.getId(), sample.getProjectDir(), commands);
    }

    private static Command removeReplaceKotlinVersionFlag(Command command) {
        List<String> newFlags = new ArrayList<>(command.getFlags());
        newFlags.remove(HAS_KOTLIN_VERSION_PLACEHOLDER_FLAG);
        return command.toBuilder()
            .setFlags(newFlags)
            .build();
    }

    private static void replaceKotlinVersionForEveryFile(Path directory) {
        try(Stream<Path> paths = Files.list(directory)) {
            paths.forEach(path -> {
                if (path.toFile().isDirectory()) {
                    replaceKotlinVersionForEveryFile(path);
                } else {
                    replaceKotlinVersionForFile(path);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void replaceKotlinVersionForFile(Path file) {
        try {
            String content = new String(Files.readAllBytes(file), UTF_8);
            if (content.contains(KOTLIN_VERSION_PLACEHOLDER)) {
                content = content.replace(KOTLIN_VERSION_PLACEHOLDER, KotlinDslVersion.current().getKotlinVersion());
                Files.write(file, content.getBytes(UTF_8));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
