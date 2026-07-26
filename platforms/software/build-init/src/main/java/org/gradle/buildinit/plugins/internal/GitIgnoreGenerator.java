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

package org.gradle.buildinit.plugins.internal;

import org.gradle.internal.UncheckedException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Spliterator;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;

public class GitIgnoreGenerator implements BuildContentGenerator {
    private static final List<String> GRADLE_CACHE_IGNORE_BLOCK = Arrays.asList(".gradle");
    private static final List<String> BUILD_OUTPUT_IGNORE_BLOCK = Arrays.asList("build/", "!**/docs/**/build/", "!**/src/**/build/");
    private static final List<String> KOTLIN_PLUGIN_IGNORE_BLOCK = Arrays.asList(".kotlin");
    private static final Pattern BUILD_PATH_COMPONENT = Pattern.compile("(^|/)build(/|$)", Pattern.CASE_INSENSITIVE);

    @Override
    public void generate(InitSettings settings, BuildContentGenerationContext buildContentGenerationContext) {
        File file = settings.getTarget().file(".gitignore").getAsFile();
        List<List<String>> gitignoreBlocksToAppend = getGitignoreBlocksToAppend(file);
        if (!gitignoreBlocksToAppend.isEmpty()) {
            boolean shouldAppendNewLine = file.exists();
            try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(file.toPath(), UTF_8, CREATE, APPEND))) {
                if (shouldAppendNewLine) {
                    writer.println();
                }
                Spliterator<List<String>> it = gitignoreBlocksToAppend.spliterator();
                if (it.tryAdvance(e -> withComment(e).forEach(writer::println))) {
                    StreamSupport.stream(it, false).forEach(e -> withSeparator(withComment(e)).forEach(writer::println));
                }
            } catch (IOException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }
    }

    @SuppressWarnings("DefaultCharset") //TODO: evaluate errorprone suppression (https://github.com/gradle/gradle/issues/35864)
    private static List<List<String>> getGitignoreBlocksToAppend(File gitignoreFile) {
        // .gradle - project cache directory, see https://docs.gradle.org/current/userguide/directory_layout.html#dir:project_root
        //  build  - build output directory, except when used as part of source or documentation paths
        // .kotlin - Kotlin Gradle Plugin caches/metadata
        List<List<String>> result = new ArrayList<>(Arrays.asList(
            GRADLE_CACHE_IGNORE_BLOCK,
            BUILD_OUTPUT_IGNORE_BLOCK,
            KOTLIN_PLUGIN_IGNORE_BLOCK
        ));
        if (gitignoreFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(gitignoreFile))){
                List<String> existingLines = reader.lines().collect(Collectors.toList());
                result.removeIf(entry -> containsExactSequence(existingLines, entry));
                if (containsBuildRule(existingLines)) {
                    result.remove(BUILD_OUTPUT_IGNORE_BLOCK);
                }
            } catch (IOException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }
        return result;
    }

    private static boolean containsExactSequence(List<String> lines, List<String> sequence) {
        for (int i = 0; i <= lines.size() - sequence.size(); i++) {
            if (lines.subList(i, i + sequence.size()).equals(sequence)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsBuildRule(List<String> lines) {
        return lines.stream()
            .map(String::trim)
            .filter(line -> !line.isEmpty() && !line.startsWith("#"))
            .map(line -> line.startsWith("!") ? line.substring(1) : line)
            .anyMatch(line -> BUILD_PATH_COMPONENT.matcher(line).find());
    }

    private static List<String> withComment(List<String> entry) {
        List<String> result = new ArrayList<>();
        String firstEntry = entry.get(0);
        if (firstEntry.startsWith(".gradle")) {
            result.add("# Ignore Gradle project-specific cache directory");
        } else if (firstEntry.startsWith("build")) {
            result.add("# Ignore Gradle build output directories, except when used as part of source or documentation paths");
        } else if (firstEntry.startsWith(".kotlin")) {
            result.add("# Ignore Kotlin plugin data");
        }
        result.addAll(entry);

        return result;
    }

    private static List<String> withSeparator(List<String> entry) {
        List<String> result = new ArrayList<>(1 + entry.size());
        result.add("");
        result.addAll(entry);
        return result;
    }
}
