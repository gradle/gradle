/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.launcher.daemon;

import org.gradle.util.GradleVersion;
import org.gradle.util.UUIDGenerator;

import java.io.File;
import java.io.FilenameFilter;
import java.util.List;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;

/**
 * @author: Szczepan Faber, created at: 8/20/11
 */
public class DaemonDir {

    private final File dir;
    public File registryFile;

    public DaemonDir(File baseDir) {
        dir = new File(baseDir, String.format("daemon/%s", GradleVersion.current().getVersion()));
        registryFile = new File(dir, "registry.bin");
        dir.mkdirs();
    }

    public File getDir() {
        return dir;
    }

    public File getRegistry() {
        return registryFile;
    }

    //very simplistic, just making sure each damon has unique log file
    public File createUniqueLog() {
        String uid = new UUIDGenerator().generateId().toString();
        return new File(dir, String.format("daemon-%s.out.log", uid));
    }

    public List<File> getLogs() {
        File[] files = dir.listFiles(new FilenameFilter() {
            public boolean accept(File file, String s) {
                return s.startsWith("daemon-") && s.endsWith("out.log");
            }
        });
        if (files != null) {
            return asList(files);
        }
        return emptyList();
    }
}
