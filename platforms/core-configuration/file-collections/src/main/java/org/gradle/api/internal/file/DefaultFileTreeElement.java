/*
 * Copyright 2009 the original author or authors.
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

import org.gradle.api.file.FilePermissions;
import org.gradle.api.file.RelativePath;
import org.gradle.api.file.SymbolicLinkDetails;
import org.gradle.internal.file.Chmod;
import org.gradle.internal.file.Stat;
import org.gradle.util.internal.GFileUtils;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;

public class DefaultFileTreeElement extends AbstractFileTreeElement {
    private final File file;
    private final RelativePath relativePath;
    private final Stat stat;

    private final boolean preserveLink;
    private Boolean isLink;
    private SymbolicLinkDetails linkDetails;

    public DefaultFileTreeElement(
        File file,
        RelativePath relativePath,
        Chmod chmod,
        Stat stat,
        boolean preserveLink
    ) {
        super(chmod);
        this.file = file;
        this.relativePath = relativePath;
        this.stat = stat;
        this.preserveLink = preserveLink;
    }

    @Deprecated
    //Backward compatibility with shadow plugin, to be removed after the plugin is updated
    public DefaultFileTreeElement(
        File file,
        RelativePath relativePath,
        Chmod chmod,
        Stat stat
    ) {
        this(file, relativePath, chmod, stat, false);
    }

    @Override
    public File getFile() {
        return file;
    }

    @Override
    public String getDisplayName() {
        return "file '" + file + "'";
    }

    @Override
    public long getLastModified() {
        return file.lastModified();
    }

    @Override
    public long getSize() {
        return file.length();
    }

    @Override
    public boolean isDirectory() {
        return !relativePath.isFile();
    }

    @Override
    public InputStream open() {
        return GFileUtils.openInputStream(file);
    }

    @Override
    public RelativePath getRelativePath() {
        return relativePath;
    }

    @Override
    public FilePermissions getPermissions() {
        int unixNumeric = stat.getUnixMode(file, !isSymbolicLink());
        return new DefaultFilePermissions(unixNumeric);
    }

    @Override
    public boolean isSymbolicLink() {
        return preserveLink && isLink();
    }

    private boolean isLink() {
        if (isLink == null) {
            // caching this for performance reasons
            isLink = Files.isSymbolicLink(file.toPath());
        }
        return isLink;
    }

    @Override
    public SymbolicLinkDetails getSymbolicLinkDetails() {
        if (isLink() && linkDetails == null) {
            linkDetails = new DefaultSymbolicLinkDetails(file.toPath(), relativePath.getSegments().length);
        }
        return linkDetails;
    }
}
