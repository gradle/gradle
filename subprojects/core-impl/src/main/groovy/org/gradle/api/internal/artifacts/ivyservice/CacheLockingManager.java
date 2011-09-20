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
package org.gradle.api.internal.artifacts.ivyservice;

import org.apache.ivy.core.settings.IvySettings;
import org.jfrog.wharf.ivy.lock.LockHolder;
import org.jfrog.wharf.ivy.lock.LockHolderFactory;
import org.jfrog.wharf.ivy.lock.LockLogger;

import java.io.File;
import java.io.IOException;

public class CacheLockingManager implements LockHolderFactory {
    public LockLogger getLogger() {
        throw new UnsupportedOperationException();
    }

    public long getTimeoutInMs() {
        throw new UnsupportedOperationException();
    }

    public long getSleepTimeInMs() {
        throw new UnsupportedOperationException();
    }

    public String getLockFileSuffix() {
        throw new UnsupportedOperationException();
    }

    public LockHolder getLockHolder(final File protectedFile) {
        return new LockHolder() {
            public void releaseLock() {
                System.out.format("Releasing '%s'%n", protectedFile);
            }

            public boolean acquireLock() {
                System.out.format("Locking '%s'%n", protectedFile);
                return true;
            }

            public File getLockFile() {
                throw new UnsupportedOperationException();
            }

            public File getProtectedFile() {
                return protectedFile;
            }

            public String stateMessage() {
                return "ok";
            }
        };
    }

    public LockHolder getOrCreateLockHolder(File protectedFile) {
        return getLockHolder(protectedFile);
    }

    public void close() throws IOException {
        throw new UnsupportedOperationException();
    }

    public void setSettings(IvySettings settings) {
        throw new UnsupportedOperationException();
    }

}
