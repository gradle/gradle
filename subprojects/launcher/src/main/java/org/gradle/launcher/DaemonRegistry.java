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

package org.gradle.launcher;

import org.gradle.util.GradleVersion;
import org.gradle.util.UUIDGenerator;

import java.io.File;
import java.io.FilenameFilter;
import java.util.List;

import static org.codehaus.groovy.runtime.InvokerHelper.asList;

/**
 * TODO SF - temporary stuff. Will be changed into the cache registry stuff recently introduced by Adam.
 *
 * Access to daemon registry files. Useful also for testing.
 * <p>
 * Busy (connected) daemons have different file names. The logic behind that is very simple and needs some more work.
 *
 * @author: Szczepan Faber, created at: 8/18/11
 */
public class DaemonRegistry {

    private final File registryFolder;

    public DaemonRegistry(File registryFolder) {
        this.registryFolder = registryFolder;
    }

    List<File> getAll() {
        return registryFiles(allFilter);
    }

    List<File> getIdle() {
        return registryFiles(idleFilter);
    }

    List<File> getBusy() {
        return registryFiles(busyFilter);
    }

    private List<File> registryFiles(FilenameFilter idleFilter) {
        File dir = new File(registryFolder, String.format("daemon/%s", GradleVersion.current().getVersion()));
        return asList(dir.listFiles(idleFilter));
    }

    RegistryFile createRegistryFileHandle() {
        //Since there are multiple daemons we need unique name of the registry file
        String uid = new UUIDGenerator().generateId().toString();
        File file = new File(registryFolder, String.format("daemon/%s/registry-%s.bin", GradleVersion.current().getVersion(), uid));
        //daemon clients work fine even if the registry file exists without working daemon, see maybeConnect() method.
        //hence if the deleteOnExit fails we don't care
        file.deleteOnExit();
        return new RegistryFile(file);
    }

    public static class RegistryFile {

        private final File file;
        private final File busyFile;

        public RegistryFile(File file) {
            this.file = file;
            busyFile = new File(file.getParentFile(), file.getName() + ".busy");
        }

        public File getFile() {
            return file;
        }

        public void markBusy() {
            //TODO SF - the rename logic ignores the failures at the moment. Also, consider making the daemon write the status to the file directly.
            file.renameTo(busyFile);
        }

        public void markIdle() {
            busyFile.renameTo(file);
        }
    }

    private FilenameFilter allFilter = new FilenameFilter() {
        public boolean accept(File file, String s) {
            return s.startsWith("registry-");
        }
    };

    private FilenameFilter idleFilter = new FilenameFilter() {
        public boolean accept(File file, String s) {
            return s.startsWith("registry-") && !s.endsWith(".busy");
        }
    };

    private FilenameFilter busyFilter = new FilenameFilter() {
        public boolean accept(File file, String s) {
            return s.startsWith("registry-") && s.endsWith(".busy");
        }
    };
}
