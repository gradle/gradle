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

package org.gradle.internal.resource.local;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;

public class ModificationTimeFileAccessTimeJournal implements FileAccessTimeJournal {
    @Override
    public void setLastAccessTime(File file, long millis) {
        try {
            Files.setLastModifiedTime(file.toPath(), FileTime.fromMillis(millis));
        } catch (IOException ignore) {
            // ignore
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
