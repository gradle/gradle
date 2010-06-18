/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.util.GUtil;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class SimpleFileCollection extends AbstractFileCollection {
    private final Set<File> files;

    public SimpleFileCollection(File... files) {
        if (files.length > 0) {
            this.files = new LinkedHashSet<File>(Arrays.asList(files));
        } else {
            this.files = Collections.emptySet();
        }
    }

    public SimpleFileCollection(Iterable<File> files) {
        this.files = new LinkedHashSet<File>();
        GUtil.addToCollection(this.files, files);
    }

    @Override
    public String getDisplayName() {
        return "file collection";
    }

    public Set<File> getFiles() {
        return files;
    }
}
