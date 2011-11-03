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
package org.gradle.launcher.daemon.registry;

import org.gradle.cache.internal.DefaultFileLockManager;
import org.gradle.cache.internal.DefaultProcessMetaDataProvider;
import org.gradle.cache.internal.FileLockManager;
import org.gradle.os.*;
import org.gradle.os.jna.NativeEnvironment;
import org.gradle.api.internal.project.DefaultServiceRegistry;

import java.io.File;

/**
 * Takes care of instantiating and wiring together the services required for a daemon registry.
 */
public class DaemonRegistryServices extends DefaultServiceRegistry {
    private final File userHomeDir;

    public DaemonRegistryServices(File userHomeDir) {
        this.userHomeDir = userHomeDir;
    }

    protected ProcessEnvironment createProcessEnvironment() {
        return NativeEnvironment.current();
    }

    protected DaemonDir createDaemonDir() {
        return new DaemonDir(userHomeDir, get(ProcessEnvironment.class));
    }

    protected FileLockManager createFileLockManager() {
        return new DefaultFileLockManager(new DefaultProcessMetaDataProvider(get(ProcessEnvironment.class)));
    }

    protected DaemonRegistry createDaemonRegistry() {
        return new PersistentDaemonRegistry(get(DaemonDir.class).getRegistry(), get(FileLockManager.class));
    }

}
