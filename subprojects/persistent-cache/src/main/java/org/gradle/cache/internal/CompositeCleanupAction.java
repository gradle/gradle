/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.cache.internal;

import com.google.common.collect.ImmutableList;
import org.gradle.cache.CleanableStore;
import org.gradle.cache.CleanupAction;
import org.gradle.internal.time.CountdownTimer;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class CompositeCleanupAction implements CleanupAction {

    public static Builder builder() {
        return new Builder();
    }

    private final List<ScopedCleanup> cleanups;

    private CompositeCleanupAction(List<ScopedCleanup> cleanups) {
        this.cleanups = cleanups;
    }

    @Override
    public void clean(CleanableStore cleanableStore, CountdownTimer timer) {
        for (ScopedCleanup scopedCleanup : cleanups) {
            if (timer.hasExpired()) {
                break;
            }
            scopedCleanup.action.clean(new CleanableSubDir(cleanableStore, scopedCleanup.baseDir), timer);
        }
    }

    public static class Builder {
        private List<ScopedCleanup> cleanups = new ArrayList<ScopedCleanup>();

        private Builder() {
        }

        public Builder add(File baseDir, CleanupAction cleanupAction) {
            cleanups.add(new ScopedCleanup(baseDir, cleanupAction));
            return this;
        }

        public CompositeCleanupAction build() {
            return new CompositeCleanupAction(ImmutableList.copyOf(cleanups));
        }
    }

    private static class ScopedCleanup {
        private final File baseDir;
        private final CleanupAction action;

        ScopedCleanup(File baseDir, CleanupAction action) {
            this.baseDir = baseDir;
            this.action = action;
        }
    }

    private static class CleanableSubDir implements CleanableStore {

        private final CleanableStore delegate;
        private final File subDir;
        private final String displayName;

        CleanableSubDir(CleanableStore delegate, File subDir) {
            this.delegate = delegate;
            this.subDir = subDir;
            this.displayName = delegate.getDisplayName() + " [subdir: " + subDir + "]";
        }

        @Override
        public String getDisplayName() {
            return displayName;
        }

        @Override
        public File getBaseDir() {
            return subDir;
        }

        @Override
        public Collection<File> getReservedCacheFiles() {
            return delegate.getReservedCacheFiles();
        }
    }
}
