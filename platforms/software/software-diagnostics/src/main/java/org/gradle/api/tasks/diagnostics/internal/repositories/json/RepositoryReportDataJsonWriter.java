/*
 * Copyright 2026 the original author or authors.
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
package org.gradle.api.tasks.diagnostics.internal.repositories.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.gradle.api.tasks.diagnostics.internal.repositories.model.RepositoryReportProjectModel;
import org.gradle.util.Path;
import org.jspecify.annotations.NullMarked;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Writes a {@link RepositoryReportProjectModel} to a JSON file using Gson default reflection.
 * <p>
 * Registers {@link PathTypeAdapter} for {@link Path} so the path is serialized as its
 * string representation rather than reflectively descending into its private fields.
 */
@NullMarked
public final class RepositoryReportDataJsonWriter {
    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .serializeNulls()
        .registerTypeAdapter(Path.class, PathTypeAdapter.INSTANCE)
        .create();

    private RepositoryReportDataJsonWriter() {}

    public static void write(RepositoryReportProjectModel model, File outputFile) {
        try {
            Files.createDirectories(outputFile.getParentFile().toPath());
            try (BufferedWriter writer = Files.newBufferedWriter(outputFile.toPath(), StandardCharsets.UTF_8)) {
                GSON.toJson(model, writer);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write " + outputFile, e);
        }
    }
}
