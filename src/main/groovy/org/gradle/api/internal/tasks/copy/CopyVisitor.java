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
package org.gradle.api.internal.tasks.copy;

import groovy.lang.Closure;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.gradle.api.GradleException;

import java.io.*;
import java.util.List;

/**
 * @author Steve Appling
 */
public class CopyVisitor implements FileVisitor {
    private static Logger logger = LoggerFactory.getLogger(CopyVisitor.class);

    private File baseDestDir, currentDestDir;
    private List<Closure> remapClosures;
    private List<NameMapper> nameMappers;
    private FilterChain filter;
    private boolean didWork = false;


    public CopyVisitor(File baseDestDir, List<Closure> remapClosures, List<NameMapper> nameMappers, FilterChain filter) {
        this.baseDestDir = baseDestDir;
        this.remapClosures = remapClosures;
        this.nameMappers = nameMappers;
        currentDestDir = baseDestDir;
        this.filter = filter;
    }

   public void visitDir(File dir, RelativePath path) {
        currentDestDir = new File(baseDestDir, path.getPathString());
    }

    public void visitFile(File source, RelativePath path) {
        File target = getTarget(path);
        if (target == null) {
            // not allowed, skip
            return;
        }
        target.getParentFile().mkdirs();
        if (needsCopy(source, target)) {
            try {
                copyFile(source, target);
            } catch (IOException e) {
                throw new GradleException("Error copying file:"+ source+" to:"+target, e);
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
            for (NameMapper nameMapper : nameMappers) {
                resultName = nameMapper.rename(targetName);
                if (resultName != null) {
                    break;
                }
            }
            targetName = resultName;
        }

        if (targetName != null) {
            File target = new File(currentDestDir, targetName);
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
        if (filter.hasChain()) {
            copyFileFiltered(srcFile, destFile);
        } else {
            copyFileStreams(srcFile,  destFile);
        }
        destFile.setLastModified(srcFile.lastModified());
    }


    private void copyFileFiltered(File srcFile, File destFile) throws IOException {
        FileReader inReader = new FileReader(srcFile);
        filter.setHead(inReader);
        FileWriter fWriter = new FileWriter(destFile);
        try {
            IOUtils.copyLarge(filter.getChain(), fWriter);
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
