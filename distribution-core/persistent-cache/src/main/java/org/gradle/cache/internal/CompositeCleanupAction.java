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
import org.gradle.cache.CleanupProgressMonitor;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class CompositeCleanupAction implements CleanupAction {

    public static Builder builder() {
        return new Builder();
    }

    private final List<CleanupAction> cleanups;

    private CompositeCleanupAction(List<CleanupAction> cleanups) {
        this.cleanups = cleanups;
    }

    @Override
    public void clean(CleanableStore cleanableStore, CleanupProgressMonitor progressMonitor) {
        for (CleanupAction action : cleanups) {
            action.clean(cleanableStore, progressMonitor);
        }
    }

    public static class Builder {
        private List<CleanupAction> cleanups = new ArrayList<CleanupAction>();

        private Builder() {
        }

        public Builder add(CleanupAction... actions) {
            Collections.addAll(cleanups, actions);
            return this;
        }

        public Builder add(File baseDir, CleanupAction... actions) {
            for (CleanupAction action : actions) {
                cleanups.add(new ScopedCleanupAction(baseDir, action));
            }
            return this;
        }

        public CompositeCleanupAction build() {
            return new CompositeCleanupAction(ImmutableList.copyOf(cleanups));
        }
    }

    private static class ScopedCleanupAction implements CleanupAction {
        private final File baseDir;
        private final CleanupAction action;

        ScopedCleanupAction(File baseDir, CleanupAction action) {
            this.baseDir = baseDir;
            this.action = action;
        }

        @Override
        public void clean(CleanableStore cleanableStore, CleanupProgressMonitor progressMonitor) {
            action.clean(new CleanableSubDir(cleanableStore, baseDir), progressMonitor);
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
