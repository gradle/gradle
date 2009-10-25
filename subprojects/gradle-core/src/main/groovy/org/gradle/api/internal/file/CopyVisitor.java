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

import groovy.lang.Closure;
import org.gradle.api.Transformer;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.RelativePath;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.tasks.WorkResult;

import java.io.*;
import java.util.List;

/**
 * @author Steve Appling
 */
public class CopyVisitor implements FileVisitor, WorkResult {
    private final File baseDestDir;
    private final List<Closure> remapClosures;
    private final List<Transformer<String>> nameMappers;
    private final FilterChain filterChain;
    private boolean didWork;
    private final Transformer<Reader> filterReaderTransformer = new Transformer<Reader>() {
        public Reader transform(Reader original) {
            filterChain.findFirstFilterChain().setInputSource(original);
            return filterChain;
        }
    };

    public CopyVisitor(File baseDestDir, List<Closure> remapClosures, List<Transformer<String>> nameMappers, FilterChain filterChain) {
        this.baseDestDir = baseDestDir;
        this.remapClosures = remapClosures;
        this.nameMappers = nameMappers;
        this.filterChain = filterChain;
    }

    public void visitDir(FileVisitDetails dirDetails) {
    }

    public void visitFile(FileVisitDetails source) {
        File target = getTarget(source.getRelativePath());
        if (target == null) {
            // not allowed, skip
            return;
        }
        copyFile(source, target);
    }

    public boolean getDidWork() {
        return didWork;
    }

    File getTarget(RelativePath path) {
        File result = null;
        String targetName = path.getLastName();
        if (nameMappers != null && nameMappers.size() != 0) {
            String resultName = null;
            for (Transformer<String> nameMapper : nameMappers) {
                resultName = nameMapper.transform(targetName);
                if (resultName != null) {
                    break;
                }
            }
            targetName = resultName;
        }

        if (targetName != null) {
            File target = new File(path.getParent().getFile(baseDestDir), targetName);
            if (remapClosures == null || remapClosures.size() == 0) {
                result = target;
            } else {
                for (Closure nextClosure : remapClosures) {
                    Object targetObj = nextClosure.call(target);
                    if (targetObj instanceof File) {
                        result = (File) targetObj;
                        break;
                    }
                }
            }
        }
        return result;
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
