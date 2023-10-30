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

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.gradle.api.UncheckedIOException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.Spliterator;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class GitIgnoreGenerator implements BuildContentGenerator {

    @Override
    public void generate(InitSettings settings, BuildContentGenerationContext buildContentGenerationContext) {
        File file = settings.getTarget().file(".gitignore").getAsFile();
        Set<String> gitignoresToAppend = getGitignoresToAppend(file);
        if (!gitignoresToAppend.isEmpty()) {
            boolean shouldAppendNewLine = file.exists();
            try (PrintWriter writer = new PrintWriter(new FileWriter(file, true))) {
                if (shouldAppendNewLine) {
                    writer.println();
                }
                Spliterator<String> it = gitignoresToAppend.spliterator();
                if (it.tryAdvance(e -> withComment(e).forEach(writer::println))) {
                    StreamSupport.stream(it, false).forEach(e -> withSeparator(withComment(e)).forEach(writer::println));
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private static Set<String> getGitignoresToAppend(File gitignoreFile) {
        Set<String> result = Sets.newLinkedHashSet(Arrays.asList(".gradle", "build"));
        if (gitignoreFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(gitignoreFile))){
                result.removeAll(reader.lines().filter(it -> result.contains(it)).collect(Collectors.toSet()));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return result;
    }

    private static List<String> withComment(String entry) {
        List<String> result = Lists.newArrayList();
        if (entry.startsWith(".gradle")) {
            result.add("# Ignore Gradle project-specific cache directory");
        } else if (entry.startsWith("build")) {
            result.add("# Ignore Gradle build output directory");
        }
        result.add(entry);

        return result;
    }

    private static List<String> withSeparator(List<String> entry) {
        List<String> result = Lists.newArrayList("");
        result.addAll(entry);
        return result;
    }
}
