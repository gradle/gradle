/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.file.copy;

import groovy.lang.Closure;
import org.gradle.api.GradleException;
import org.gradle.api.file.ContentFilterable;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.file.AbstractFileTreeElement;
import org.gradle.internal.nativeplatform.filesystem.Chmod;

import java.io.*;
import java.util.Map;

public class DefaultFileCopyDetails extends AbstractFileTreeElement implements FileVisitDetails, FileCopyDetailsInternal {
    private final FileVisitDetails fileDetails;
    private final CopySpecInternal spec;
    private final FilterChain filterChain = new FilterChain();
    private RelativePath relativePath;
    private boolean excluded;
    private Integer mode;
    private DuplicatesStrategy duplicatesStrategy;

    public DefaultFileCopyDetails(FileVisitDetails fileDetails, CopySpecInternal spec, Chmod chmod) {
        super(chmod);
        this.fileDetails = fileDetails;
        this.spec = spec;
        this.duplicatesStrategy = spec.getDuplicatesStrategy();
    }

    public boolean isIncludeEmptyDirs() {
        return spec.getIncludeEmptyDirs();
    }

    public String getDisplayName() {
        return fileDetails.toString();
    }

    public void stopVisiting() {
        fileDetails.stopVisiting();
    }

    public File getFile() {
        if (filterChain.hasFilters()) {
            throw new UnsupportedOperationException();
        } else {
            return fileDetails.getFile();
        }
    }

    public boolean isDirectory() {
        return fileDetails.isDirectory();
    }

    public long getLastModified() {
        return fileDetails.getLastModified();
    }

    public long getSize() {
        if (filterChain.hasFilters()) {
            ByteCountingOutputStream outputStream = new ByteCountingOutputStream();
            copyTo(outputStream);
            return outputStream.size;
        } else {
            return fileDetails.getSize();
        }
    }

    public InputStream open() {
        if (filterChain.hasFilters()) {
            return filterChain.transform(fileDetails.open());
        } else {
            return fileDetails.open();
        }
    }

    public void copyTo(OutputStream outstr) {
        if (filterChain.hasFilters()) {
            super.copyTo(outstr);
        } else {
            fileDetails.copyTo(outstr);
        }
    }

    public boolean copyTo(File target) {
        if (filterChain.hasFilters()) {
            return super.copyTo(target);
        } else {
            final boolean copied = fileDetails.copyTo(target);
            adaptPermissions(target);
            return copied;
        }
    }

    private void adaptPermissions(File target) {
        final Integer specMode = getMode();
        if(specMode !=null){
            try {
                getChmod().chmod(target, specMode);
            } catch (IOException e) {
                throw new GradleException(String.format("Could not set permission %s on '%s'.", specMode, target), e);
            }
        }
    }

    public RelativePath getRelativePath() {
        if (relativePath == null) {
            RelativePath path = fileDetails.getRelativePath();
            relativePath = spec.getDestPath().append(path.isFile(), path.getSegments());
        }
        return relativePath;
    }

    public int getMode() {
        if (mode != null) {
            return mode;
        }

        Integer specMode = getSpecMode();
        if (specMode != null) {
            return specMode;
        }

        return fileDetails.getMode();
    }

    private Integer getSpecMode() {
        return fileDetails.isDirectory() ? spec.getDirMode() : spec.getFileMode();
    }

    public void setRelativePath(RelativePath path) {
        this.relativePath = path;
    }

    public void setName(String name) {
        relativePath = getRelativePath().replaceLastName(name);
    }

    public void setPath(String path) {
        relativePath = RelativePath.parse(getRelativePath().isFile(), path);
    }

    boolean isExcluded() {
        return excluded;
    }

    public void exclude() {
        excluded = true;
    }

    public void setMode(int mode) {
        this.mode = mode;
    }

    public ContentFilterable filter(Closure closure) {
        filterChain.add(closure);
        return this;
    }

    public ContentFilterable filter(Map<String, ?> properties, Class<? extends FilterReader> filterType) {
        filterChain.add(filterType, properties);
        return this;
    }

    public ContentFilterable filter(Class<? extends FilterReader> filterType) {
        filterChain.add(filterType);
        return this;
    }

    public ContentFilterable expand(Map<String, ?> properties) {
        filterChain.expand(properties);
        return this;
    }

    public void setDuplicatesStrategy(DuplicatesStrategy strategy) {
        this.duplicatesStrategy = strategy;
    }

    public DuplicatesStrategy getDuplicatesStrategy() {
        return this.duplicatesStrategy;
    }

    private static class ByteCountingOutputStream extends OutputStream {
        long size;

        @Override
        public void write(int b) throws IOException {
            size++;
        }

        @Override
        public void write(byte[] b) throws IOException {
            size += b.length;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            size += len;
        }
    }
}
