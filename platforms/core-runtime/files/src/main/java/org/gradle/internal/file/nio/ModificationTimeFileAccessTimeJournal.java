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

package org.gradle.internal.file.nio;

import org.gradle.internal.file.FileAccessTimeJournal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;

@SuppressWarnings("Since15")
public class ModificationTimeFileAccessTimeJournal implements FileAccessTimeJournal {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModificationTimeFileAccessTimeJournal.class);

    @Override
    public void setLastAccessTime(File file, long millis) {
        try {
            Files.setLastModifiedTime(file.toPath(), FileTime.fromMillis(millis));
        } catch (IOException e) {
            LOGGER.debug("Ignoring failure to set last access time of " + file, e);
        }
    }

    @Override
    public long getLastAccessTime(File file) {
        return file.lastModified();
    }

    @Override
    public void deleteLastAccessTime(File file) {
        // nothing to do
    }
}
