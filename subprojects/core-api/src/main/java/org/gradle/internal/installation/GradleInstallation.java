/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.internal.installation;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class GradleInstallation {

    public static final FileFilter DIRECTORY_FILTER = new FileFilter() {
        @Override
        public boolean accept(File pathname) {
            return pathname.isDirectory();
        }
    };

    private final File dir;
    private final List<File> libDirs;

    public GradleInstallation(File dir) {
        this.dir = dir;
        this.libDirs = Collections.unmodifiableList(findLibDirs(dir));
    }

    public File getGradleHome() {
        return dir;
    }

    public List<File> getLibDirs() {
        return libDirs;
    }

    private static List<File> findLibDirs(File dir) {
        List<File> libDirAndSubdirs = new ArrayList<File>();
        collectWithSubdirectories(new File(dir, "lib"), libDirAndSubdirs);
        return libDirAndSubdirs;
    }

    private static void collectWithSubdirectories(File root, Collection<File> collection) {
        collection.add(root);
        File[] subDirs = root.listFiles(DIRECTORY_FILTER);
        if (subDirs != null) {
            for (File subdirectory : subDirs) {
                collectWithSubdirectories(subdirectory, collection);
            }
        }
    }

}
