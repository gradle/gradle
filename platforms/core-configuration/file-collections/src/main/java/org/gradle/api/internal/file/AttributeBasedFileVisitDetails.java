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
import org.gradle.api.file.SymbolicLinkDetails;
import org.gradle.internal.file.Chmod;
import org.gradle.internal.file.Stat;

import javax.annotation.Nullable;
import java.io.File;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.atomic.AtomicBoolean;

public class AttributeBasedFileVisitDetails extends DefaultFileVisitDetails {
    private final BasicFileAttributes attributes;

    public AttributeBasedFileVisitDetails(
        File file,
        RelativePath relativePath,
        AtomicBoolean stop,
        Chmod chmod,
        Stat stat,
        BasicFileAttributes attributes,
        @Nullable SymbolicLinkDetails linkDetails,
        boolean preserveLink
    ) {
        super(file, relativePath, stop, chmod, stat, linkDetails, preserveLink);
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

    @Override
    public boolean isSymbolicLink() {
        // attributes.isSymbolicLink() will only return true if the file is a broken link
        // need this check for LinksStrategy.FOLLOW
        return attributes.isSymbolicLink() || super.isSymbolicLink();
    }
}
