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

package org.gradle.api.internal.file;

import org.gradle.api.file.FileSystemLocation;

import java.io.File;

public class DefaultFileSystemLocation implements FileSystemLocation {
    private final File file;

    public DefaultFileSystemLocation(File file) {
        this.file = file;
    }

    @Override
    public File getAsFile() {
        return file;
    }

    @Override
    public String toString() {
        return file.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }
        DefaultFileSystemLocation other = (DefaultFileSystemLocation) obj;
        return other.file.equals(file);
    }

    @Override
    public int hashCode() {
        return file.hashCode();
    }
}
