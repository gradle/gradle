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

import org.gradle.api.Transformer;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.tasks.WorkResult;

import java.io.File;
import java.io.Reader;

/**
 * @author Steve Appling
 */
public class CopyVisitor implements CopySpecVisitor, WorkResult {
    private File baseDestDir;
    private CopyDestinationMapper destinationMapper;
    private FilterChain filterChain;
    private boolean didWork;
    private final Transformer<Reader> filterReaderTransformer = new Transformer<Reader>() {
        public Reader transform(Reader original) {
            filterChain.findFirstFilterChain().setInputSource(original);
            return filterChain;
        }
    };

    public void visitSpec(CopySpecImpl spec) {
        baseDestDir = spec.getDestDir();
        destinationMapper = spec.getDestinationMapper();
        filterChain = spec.getFilterChain();
    }

    public void visitDir(FileVisitDetails dirDetails) {
    }

    public void visitFile(FileVisitDetails source) {
        File target = getTarget(source);
        copyFile(source, target);
    }

    public boolean getDidWork() {
        return didWork;
    }

    File getTarget(FileTreeElement source) {
        return destinationMapper.getPath(source).getFile(baseDestDir);
    }

    void copyFile(FileTreeElement srcFile, File destFile) {
        boolean copied;
        if (filterChain.hasFilters()) {
            copied = srcFile.copyTo(destFile, filterReaderTransformer);
        } else {
            copied = srcFile.copyTo(destFile);
        }
        if (copied) {
            didWork = true;
        }
    }
}
