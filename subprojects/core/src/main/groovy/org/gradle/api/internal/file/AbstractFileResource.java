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

package org.gradle.api.internal.file;

import org.gradle.api.resources.internal.ReadableResourceInternal;

import java.io.File;
import java.net.URI;

public abstract class AbstractFileResource implements ReadableResourceInternal {

    protected final File file;

    public AbstractFileResource(File file) {
        assert file != null;
        this.file = file;
    }

    public String getDisplayName() {
        return file.getAbsolutePath();
    }

    public URI getURI() {
        return file.toURI();
    }

    public String getBaseName() {
        return file.getName();
    }

    public File getFile() {
        return file;
    }

    public File getBackingFile() {
        return getFile();
    }
}
