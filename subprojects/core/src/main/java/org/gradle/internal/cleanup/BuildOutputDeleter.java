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
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.FileUtils;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.util.List;

public class BuildOutputDeleter {

    private Logger logger = Logging.getLogger(BuildOutputDeleter.class);

    void setLogger(Logger logger) {
        this.logger = logger;
    }

    public void delete(List<File> outputs) {
        for (File output : FileUtils.calculateRoots(outputs)) {
            deleteOutput(output);
        }
    }

    private void deleteOutput(File output) {
        try {
            if (output.isDirectory()) {
                forceDelete(output);
                logger.quiet(createMessage("directory", output));
            } else if (output.isFile()) {
                forceDelete(output);
                logger.quiet(createMessage("file", output));
            }
        } catch (UncheckedIOException e) {
            logger.warn(String.format("Unable to clean up '%s'", output));
        }
    }

    private String createMessage(String type, File output) {
        return String.format("Cleaned up %s '%s'", type, output);
    }

    private void forceDelete(File output) {
        GFileUtils.forceDelete(output);
    }
}
