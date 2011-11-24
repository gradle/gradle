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

package org.gradle.launcher.daemon.client;

import org.gradle.cache.internal.FileLockManager;
import org.gradle.launcher.daemon.registry.PersistentDaemonRegistry;

import java.io.File;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * by Szczepan Faber, created at: 11/24/11
 */
public class DaemonRegistryFactory {

    private final Lock lock = new ReentrantLock();

    public PersistentDaemonRegistry synchronizedRegistry(File daemonRegistryFile, FileLockManager fileLockManager) {
        return new PersistentDaemonRegistry(lock, daemonRegistryFile, fileLockManager);
    }
}
