/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.internal.changedetection.state;

import org.gradle.api.internal.file.archive.ZipEntry;
import org.gradle.internal.file.FilePathUtil;
import org.gradle.internal.fingerprint.hashing.ZipEntryContext;

import java.util.function.Supplier;

public class DefaultZipEntryContext implements ZipEntryContext {
    private final ZipEntry entry;
    private final String fullName;
    private final String rootParentName;

    public DefaultZipEntryContext(ZipEntry entry, String fullName, String rootParentName) {
        this.entry = entry;
        this.fullName = fullName;
        this.rootParentName = rootParentName;
    }

    @Override
    public ZipEntry getEntry() {
        return entry;
    }

    @Override
    public String getFullName() {
        return fullName;
    }

    @Override
    public String getRootParentName() {
        return rootParentName;
    }

    @Override
    public Supplier<String[]> getRelativePathSegments() {
        return new ZipEntryRelativePath(entry);
    }

    private static class ZipEntryRelativePath implements Supplier<String[]> {
        private final ZipEntry zipEntry;

        ZipEntryRelativePath(ZipEntry zipEntry) {
            this.zipEntry = zipEntry;
        }

        @Override
        public String[] get() {
            return FilePathUtil.getPathSegments(zipEntry.getName());
        }
    }
}
