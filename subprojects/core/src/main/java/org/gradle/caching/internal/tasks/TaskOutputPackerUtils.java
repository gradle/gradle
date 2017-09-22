/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.caching.internal.tasks;

import org.apache.commons.io.FileUtils;
import org.gradle.api.internal.tasks.OutputType;

import java.io.File;
import java.io.IOException;

/**
 * Packages task output to a ZIP file.
 */
public class TaskOutputPackerUtils {
    public static void ensureDirectoryForProperty(OutputType outputType, File specRoot) throws IOException {
        switch (outputType) {
            case DIRECTORY:
                if (!makeDirectory(specRoot)) {
                    FileUtils.cleanDirectory(specRoot);
                }
                break;
            case FILE:
                if (!makeDirectory(specRoot.getParentFile())) {
                    if (specRoot.exists()) {
                        FileUtils.forceDelete(specRoot);
                    }
                }
                break;
            default:
                throw new AssertionError();
        }
    }

    public static boolean makeDirectory(File output) throws IOException {
        if (output.isDirectory()) {
            return false;
        } else if (output.isFile()) {
            FileUtils.forceDelete(output);
        }
        FileUtils.forceMkdir(output);
        return true;
    }
}
