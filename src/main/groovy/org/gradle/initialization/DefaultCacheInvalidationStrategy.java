/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.initialization;

import org.gradle.util.WrapUtil;
import org.gradle.util.GradleUtil;
import org.gradle.CacheUsage;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.AbstractFileFilter;

import java.io.File;
import java.util.List;
import java.util.ArrayList;

/**
 * @author Hans Dockter
 */
public class DefaultCacheInvalidationStrategy implements CacheInvalidationStrategy {
    private static final List<String> IGNORE_DIR_FLOATING_LOCATION_NAMES = WrapUtil.toList(".svn");
    private static final List<String> IGNORE_DIR_FIXED_LOCATION_NAMES = WrapUtil.toList("build", ".gradle");

    public boolean isValid(File artifactFile, File rootDir) {
        if (!artifactFile.isFile()) {
            return false;
        }
        long timestamp = artifactFile.lastModified();
        return projectFilesAreOlderThanArtifact(rootDir, timestamp);
    }


    private boolean projectFilesAreOlderThanArtifact(File rootDir, final long timestamp) {
        final DirFilter dirFilter = new DirFilter(rootDir);
        IOFileFilter lastModifiedFilter = new AbstractFileFilter() {
            @Override
            public boolean accept(File file) {
                if (file.isDirectory() && !dirFilter.accept(file)) {
                    return false;
                }
                if (file.lastModified() > timestamp) {
                    throw new InvalidCacheException();
                }
                return false;
            }
        };
        try {
            FileUtils.listFiles(rootDir, lastModifiedFilter, new DirFilter(rootDir));
        } catch (InvalidCacheException e) {
            return false;
        }
        return true;
    }

    private class InvalidCacheException extends RuntimeException {

    }

    private static class DirFilter extends AbstractFileFilter {
        private List<File> fixedLocationFiles;

        private DirFilter(File rootDir) {
            this.fixedLocationFiles = getFixedLocationFiles(rootDir, IGNORE_DIR_FIXED_LOCATION_NAMES);
        }

        @Override
        public boolean accept(File file) {
            if (IGNORE_DIR_FLOATING_LOCATION_NAMES.contains(file.getName()) || fixedLocationFiles.contains(file)) {
                return false;
            }
            return true;
        }

        private List<File> getFixedLocationFiles(File rootDir, List<String> names) {
            ArrayList<File> files = new ArrayList<File>();
            for (String name : names) {
                files.add(new File(rootDir, name));
            }
            return files;
        }
    }
}
