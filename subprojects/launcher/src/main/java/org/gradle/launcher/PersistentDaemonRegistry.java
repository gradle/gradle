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

import org.gradle.cache.DefaultSerializer;
import org.gradle.cache.PersistentStateCache;
import org.gradle.cache.internal.DefaultFileLockManager;
import org.gradle.cache.internal.OnDemandFileLock;
import org.gradle.cache.internal.SimpleStateCache;
import org.gradle.messaging.remote.Address;
import org.gradle.util.UUIDGenerator;

import java.io.File;
import java.io.FilenameFilter;
import java.util.LinkedList;
import java.util.List;

import static org.codehaus.groovy.runtime.InvokerHelper.asList;

/**
 * Access to daemon registry files. Useful also for testing.
 *
 * @author: Szczepan Faber, created at: 8/18/11
 */
public class PersistentDaemonRegistry implements DaemonRegistry {

    final File registryFolder;
    final DaemonDir daemonDir;

    public PersistentDaemonRegistry(File baseFolder) {
        this.daemonDir = new DaemonDir(baseFolder);
        this.registryFolder = daemonDir.getFile();
    }

    public List<DaemonStatus> getAll() {
        List<DaemonStatus> out = new LinkedList<DaemonStatus>();
        List<File> files = getAllFiles();
        for (File file : files) {
            DaemonStatus daemonStatus = openRegistry(file).get();
            if (daemonStatus != null) {
                out.add(daemonStatus);
            }
        }
        return out;
    }

    //daemons without active connection
    public List<DaemonStatus> getIdle() {
        List<DaemonStatus> out = new LinkedList<DaemonStatus>();
        List<DaemonStatus> all = getAll();
        for (DaemonStatus d : all) {
            if (d.isIdle()) {
                out.add(d);
            }
        }
        return out;
    }

    //daemons with active connection.
    public List<DaemonStatus> getBusy() {
        List<DaemonStatus> out = new LinkedList<DaemonStatus>();
        List<DaemonStatus> all = getAll();
        for (DaemonStatus d : all) {
            if (!d.isIdle()) {
                out.add(d);
            }
        }
        return out;
    }

    private static PersistentStateCache<DaemonStatus> openRegistry(File registryFile) {
        return new SimpleStateCache<DaemonStatus>(registryFile,
                new OnDemandFileLock(registryFile, "daemon address registry", new DefaultFileLockManager()),
                new DefaultSerializer<DaemonStatus>());
    }

    private List<File> registryFiles(FilenameFilter filter) {
        return asList(registryFolder.listFiles(filter));
    }

    public List<File> getAllFiles() {
        return registryFiles(allFilter);
    }

    public Entry newEntry() {
        //Since there are multiple daemons we need unique name of the registry file
        String uid = new UUIDGenerator().generateId().toString();
        File file = new File(registryFolder, String.format("registry-%s.bin", uid)); //TODO SF move to daemon dir

        return new PersistentEntry(file);
    }

    public static class PersistentEntry implements Entry {

        private final File file;
        private Address address;

        public PersistentEntry(File file) {
            this.file = file;
        }

        public File getFile() {
            return file;
        }

        public void markBusy() {
            openRegistry(file).set(new DaemonStatus(address).setIdle(false));
        }

        public void markIdle() {
            openRegistry(file).set(new DaemonStatus(address).setIdle(true));
        }

        public void store(Address address) {
            this.address = address;
            openRegistry(file).set(new DaemonStatus(this.address));
        }

        public void remove() {
            openRegistry(file).set(null);
            //TODO SF - delete should be on the registry and should get rid of the remaining 'lock' files probably
            file.delete();
            file.deleteOnExit();
        }
    }

    private FilenameFilter allFilter = new FilenameFilter() {
        public boolean accept(File file, String s) {
            return s.startsWith("registry-") && s.endsWith(".bin"); //TODO SF move to daemon dir
        }
    };
}
