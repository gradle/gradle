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
import org.apache.commons.io.IOUtils;
import org.gradle.api.GradleException;
import org.gradle.api.Transformer;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.RelativePath;
import org.gradle.api.tasks.WorkResult;

import java.io.*;
import java.util.List;

/**
 * @author Steve Appling
 */
public class CopyVisitor implements FileVisitor, WorkResult {
    private File baseDestDir;
    private List<Closure> remapClosures;
    private List<Transformer<String>> nameMappers;
    private FilterChain filterChain;
    private boolean didWork = false;

    public CopyVisitor(File baseDestDir, List<Closure> remapClosures, List<Transformer<String>> nameMappers, FilterChain filterChain) {
        this.baseDestDir = baseDestDir;
        this.remapClosures = remapClosures;
        this.nameMappers = nameMappers;
        this.filterChain = filterChain;
    }

    public void visitDir(FileVisitDetails dirDetails) {
    }

    public void visitFile(FileVisitDetails fileDetails) {
        File source = fileDetails.getFile();
        File target = getTarget(fileDetails.getRelativePath());
        if (target == null) {
            // not allowed, skip
            return;
        }
        target.getParentFile().mkdirs();
        if (needsCopy(source, target)) {
            try {
                copyFile(source, target);
            } catch (IOException e) {
                throw new GradleException("Error copying file:" + source + " to:" + target, e);
            }
        }
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

    void copyFile(File srcFile, File destFile) throws IOException {
        didWork = true;
        if (filterChain.hasFilters()) {
            copyFileFiltered(srcFile, destFile);
        } else {
            copyFileStreams(srcFile,  destFile);
        }
        destFile.setLastModified(srcFile.lastModified());
    }


    private void copyFileFiltered(File srcFile, File destFile) throws IOException {
        FileReader inReader = new FileReader(srcFile);
        filterChain.findFirstFilterChain().setInputSource(inReader);
        FileWriter fWriter = new FileWriter(destFile);
        try {
            IOUtils.copyLarge(filterChain, fWriter);
            fWriter.flush();
        } finally {
            IOUtils.closeQuietly(inReader);
            IOUtils.closeQuietly(fWriter);
        }
    }

    private void copyFileStreams(File srcFile, File destFile) throws IOException {
        FileInputStream input = new FileInputStream(srcFile);
        FileOutputStream output = new FileOutputStream(destFile);
        try {
            IOUtils.copyLarge(input, output);
        } finally {
            IOUtils.closeQuietly(input);
            IOUtils.closeQuietly(output);
        }
    }

    boolean needsCopy(File source, File dest) {
        boolean result = true;
        if (dest.exists()) {
            if (source.lastModified() <= dest.lastModified()) {
                result = false;
            }
            // possibly add option to check file size too
        }
        return result;
    }
}
