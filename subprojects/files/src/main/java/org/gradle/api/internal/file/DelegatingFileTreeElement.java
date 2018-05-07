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

package org.gradle.api.internal.file;

import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.RelativePath;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;

public class DelegatingFileTreeElement implements FileTreeElement {

    protected final FileTreeElement delegate;

    public DelegatingFileTreeElement(FileTreeElement delegate) {
        this.delegate = delegate;
    }

    @Override
    public File getFile() {
        return delegate.getFile();
    }

    @Override
    public boolean isDirectory() {
        return delegate.isDirectory();
    }

    @Override
    public long getLastModified() {
        return delegate.getLastModified();
    }

    @Override
    public long getSize() {
        return delegate.getSize();
    }

    @Override
    public InputStream open() {
        return delegate.open();
    }

    @Override
    public void copyTo(OutputStream output) {
        delegate.copyTo(output);
    }

    @Override
    public boolean copyTo(File target) {
        return delegate.copyTo(target);
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public String getPath() {
        return delegate.getPath();
    }

    @Override
    public RelativePath getRelativePath() {
        return delegate.getRelativePath();
    }

    @Override
    public int getMode() {
        return delegate.getMode();
    }
}
