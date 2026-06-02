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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Reads a {@link RepositoryReportProjectModel} from a JSON file produced by
 * {@link RepositoryReportDataJsonWriter}.
 * <p>
 * Registers {@link PathTypeAdapter} so {@link Path} is reconstructed via its
 * canonical {@link Path#path(String)} factory rather than reflectively populating
 * private fields.
 */
@NullMarked
public final class RepositoryReportDataJsonReader {
    private static final Gson GSON = new GsonBuilder()
        .registerTypeAdapter(Path.class, PathTypeAdapter.INSTANCE)
        .create();

    private RepositoryReportDataJsonReader() {}

    public static RepositoryReportProjectModel read(File inputFile) {
        try (BufferedReader reader = Files.newBufferedReader(inputFile.toPath(), StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, RepositoryReportProjectModel.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + inputFile, e);
        }
    }
}
