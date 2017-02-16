/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.cleanup;

import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.file.delete.Deleter;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.FileUtils;

import java.io.File;

public class DefaultBuildOutputDeleter implements BuildOutputDeleter {
    private final Logger logger = Logging.getLogger(DefaultBuildOutputDeleter.class);

    private final Deleter deleter;

    public DefaultBuildOutputDeleter(Deleter deleter) {
        this.deleter = deleter;
    }

    @Override
    public void delete(final Iterable<File> outputs) {
        for (File output : FileUtils.calculateRoots(outputs)) {
            deleteOutput(output);
        }
    }

    private void deleteOutput(final File output) {
        try {
            if (output.isDirectory()) {
                deleter.delete(output);
                logger.quiet("Cleaned up directory '{}'", output);
            } else if (output.isFile()) {
                deleter.delete(output);
                logger.quiet("Cleaned up file '{}'", output);
            }
        } catch (UncheckedIOException e) {
            logger.warn("Unable to clean up '{}'", output);
        }
    }
}
