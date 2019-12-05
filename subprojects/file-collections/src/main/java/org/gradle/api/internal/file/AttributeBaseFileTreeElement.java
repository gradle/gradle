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

import org.gradle.api.file.RelativePath;
import org.gradle.internal.file.Chmod;
import org.gradle.internal.file.Stat;

import java.io.File;
import java.nio.file.attribute.BasicFileAttributes;

public class AttributeBaseFileTreeElement extends DefaultFileTreeElement {
    private final BasicFileAttributes attributes;

    public AttributeBaseFileTreeElement(File file, RelativePath relativePath, Chmod chmod, Stat stat, BasicFileAttributes attributes) {
        super(file, relativePath, chmod, stat);
        this.attributes = attributes;
    }

    @Override
    public long getSize() {
        return attributes.size();
    }

    @Override
    public long getLastModified() {
        return attributes.lastModifiedTime().toMillis();
    }
}
