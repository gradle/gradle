/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.internal.execution.history.changes;

import com.google.common.collect.ImmutableList;

import java.util.List;

/**
 * Provides an efficient summary of the changes, without doing too much unnecessary work.
 * - Will only emit changes of a single type (from a single delegate change set)
 */
public class SummarizingChangeContainer implements ChangeContainer {
    private final List<ChangeContainer> sources;

    public SummarizingChangeContainer(ChangeContainer... sources) {
        this.sources = ImmutableList.copyOf(sources);
    }

    @Override
    public boolean accept(ChangeVisitor visitor) {
        ChangeDetectingVisitor changeDetectingVisitor = new ChangeDetectingVisitor(visitor);
        for (ChangeContainer source : sources) {
            if (!source.accept(changeDetectingVisitor)) {
                return false;
            }
            if (changeDetectingVisitor.isChangesDetected()) {
                return true;
            }
        }
        return true;
    }

    private static class ChangeDetectingVisitor implements ChangeVisitor {
        private final ChangeVisitor delegate;
        private boolean changesDetected;

        public ChangeDetectingVisitor(ChangeVisitor delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean visitChange(Change change) {
            changesDetected = true;
            return delegate.visitChange(change);
        }

        public boolean isChangesDetected() {
            return changesDetected;
        }
    }
}
