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

package org.gradle.api.internal.resources;

import com.google.common.io.Files;
import org.gradle.api.internal.file.TemporaryFileProvider;
import org.gradle.api.resources.ResourceException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

/**
 * Helper for reading files in a possible different charsets.
 */
class FileResourceHelper {

    /**
     * @param tempFileProvider the temporary file provider that needs to be used if temporary file needs to be created
     * @param originalFile the original file
     * @param targetCharset that charset of the target file
     * @param sourceCharset the source charset
     * @param displayName the display name that needs to be used if an error occurred
     * @param prefix the prefix that needs to be used when creating a temporary file
     * @return file with the {@code targetCharset}
     */
    static File asFile(TemporaryFileProvider tempFileProvider, File originalFile, String targetCharset, Charset sourceCharset, String displayName, String prefix) {
        Charset targetCharsetObj = Charset.forName(targetCharset);
        if (targetCharsetObj.equals(sourceCharset)) {
            return originalFile;
        }

        File targetFile = tempFileProvider.createTemporaryFile(prefix, ".txt", "resource");
        try {
            Files.asCharSource(originalFile, sourceCharset).copyTo(Files.asCharSink(targetFile, targetCharsetObj));
            return targetFile;
        } catch (IOException e) {
            throw new ResourceException("Could not write " + displayName + " content to " + targetFile + ".", e);
        }
    }
}
