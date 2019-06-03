/*
 * Copyright 2019 the original author or authors.
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

import java.io.InputStream;
import java.util.function.Supplier;

class JdkZipEntry implements ZipEntry {

    private final java.util.zip.ZipEntry entry;
    private final Supplier<InputStream> inputStreamSupplier;

    public JdkZipEntry(java.util.zip.ZipEntry entry, Supplier<InputStream> inputStreamSupplier) {
        this.entry = entry;
        this.inputStreamSupplier = inputStreamSupplier;
    }

    @Override
    public boolean isDirectory() {
        return entry.isDirectory();
    }

    @Override
    public String getName() {
        return entry.getName();
    }

    @Override
    public InputStream getInputStream() {
        return inputStreamSupplier.get();
    }

    @Override
    public int size() {
        return (int) entry.getSize();
    }
}
