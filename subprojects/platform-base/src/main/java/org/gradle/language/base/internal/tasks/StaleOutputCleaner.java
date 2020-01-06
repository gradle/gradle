/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.language.base.internal.tasks;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.UncheckedIOException;
import org.gradle.internal.execution.impl.OutputsCleaner;
import org.gradle.internal.file.Deleter;
import org.gradle.internal.file.FileType;

import javax.annotation.CheckReturnValue;
import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class StaleOutputCleaner {

    /**
     * Clean up the given stale output files under the given directory.
     *
     * Any files and directories are removed that are descendants of {@code directoryToClean}.
     * Files and directories outside {@code directoryToClean} and {@code directoryToClean} itself is not deleted.
     *
     * Returns {code true} if any file or directory was deleted, {@code false} otherwise.
     */
    @CheckReturnValue
    public static boolean cleanOutputs(Deleter deleter, Iterable<File> filesToDelete, File directoryToClean) {
        return cleanOutputs(deleter, filesToDelete, ImmutableSet.of(directoryToClean));
    }

    /**
     * Clean up the given stale output files under the given directories.
     *
     * Any files and directories are removed that are descendants of any of the {@code directoriesToClean}.
     * Files and directories outside {@code directoriesToClean} and {@code directoriesToClean} themselves are not deleted.
     *
     * Returns {code true} if any file or directory was deleted, {@code false} otherwise.
     */
    @CheckReturnValue
    public static boolean cleanOutputs(Deleter deleter, Iterable<File> filesToDelete, ImmutableSet<File> directoriesToClean) {
        Set<String> prefixes = directoriesToClean.stream()
            .map(directoryToClean -> directoryToClean.getAbsolutePath() + File.separator)
            .collect(Collectors.toSet());

        OutputsCleaner outputsCleaner = new OutputsCleaner(
            deleter,
            file -> {
                String absolutePath = file.getAbsolutePath();
                return prefixes.stream()
                    .anyMatch(absolutePath::startsWith);
            },
            dir -> !directoriesToClean.contains(dir)
        );

        try {
            for (File f : filesToDelete) {
                if (f.isFile()) {
                    outputsCleaner.cleanupOutput(f, FileType.RegularFile);
                }
            }
            outputsCleaner.cleanupDirectories();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to clean up stale outputs", e);
        }

        return outputsCleaner.getDidWork();
    }
}
