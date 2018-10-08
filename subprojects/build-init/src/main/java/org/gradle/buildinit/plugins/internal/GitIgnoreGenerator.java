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

import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.file.FileResolver;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

public class GitIgnoreGenerator implements BuildContentGenerator {
    private final FileResolver fileResolver;

    public GitIgnoreGenerator(FileResolver fileResolver) {
        this.fileResolver = fileResolver;
    }

    @Override
    public void generate(InitSettings settings) {
        File file = fileResolver.resolve(".gitignore");
        try {
            PrintWriter writer = new PrintWriter(new FileWriter(file));
            try {
                writer.println("# Ignore Gradle project-specific cache directory");
                writer.println(".gradle");
                writer.println();
                writer.println("# Ignore Gradle build output directory");
                writer.println("build");
            } finally {
                writer.close();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
