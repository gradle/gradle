/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.plugins.ide.internal.tooling.eclipse;

import org.gradle.tooling.internal.protocol.eclipse.EclipseSourceDirectoryVersion1;

import java.io.File;
import java.io.Serializable;

public class DefaultEclipseSourceDirectory implements EclipseSourceDirectoryVersion1, Serializable {
    private final String path;
    private final File directory;

    public DefaultEclipseSourceDirectory(String path, File directory) {
        this.path = path;
        this.directory = directory;
    }

    @Override
    public String toString() {
        return String.format("source directory '%s'", path);
    }

    public File getDirectory() {
        return directory;
    }

    public String getPath() {
        return path;
    }
}
