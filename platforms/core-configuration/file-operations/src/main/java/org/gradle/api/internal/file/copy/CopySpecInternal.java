/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.file.copy;

import org.gradle.api.Action;
import org.gradle.api.file.FileCopyDetails;
import org.gradle.api.file.SyncSpec;
import org.gradle.api.tasks.util.PatternFilterable;
import org.jspecify.annotations.Nullable;

import java.io.File;

public interface CopySpecInternal extends SyncSpec {

    Iterable<CopySpecInternal> getChildren();

    CopySpecInternal addChild();

    CopySpecInternal addChildBeforeSpec(CopySpecInternal childSpec);

    CopySpecInternal addFirst();

    void walk(Action<? super CopySpecResolver> action);

    CopySpecResolver buildRootResolver();

    CopySpecResolver buildResolverRelativeToParent(CopySpecResolver parent);

    void addChildSpecListener(CopySpecListener listener);

    void visit(CopySpecAddress parentPath, CopySpecVisitor visitor);

    /**
     * Returns whether the spec, or any of its children have custom copy actions.
     */
    boolean hasCustomActions();

    void appendCachingSafeCopyAction(Action<? super FileCopyDetails> action);

    @Override
    PatternFilterable getPreserve();

    @Override
    CopySpecInternal preserve(Action<? super PatternFilterable> action);

    @Nullable
    File getDestinationDir();

    /**
     * Listener triggered when a spec is added to the hierarchy.
     */
    interface CopySpecListener {
        void childSpecAdded(CopySpecAddress path, CopySpecInternal spec);
    }

    /**
     * A visitor to traverse the spec hierarchy.
     */
    interface CopySpecVisitor {
        void visit(CopySpecAddress address, CopySpecInternal spec);
    }

    /**
     * The address of a spec relative to its parent.
     */
    interface CopySpecAddress {
        @Nullable
        CopySpecAddress getParent();

        CopySpecInternal getSpec();

        int getAdditionIndex();

        CopySpecAddress append(CopySpecInternal spec, int additionIndex);

        CopySpecAddress append(CopySpecAddress relativeAddress);

        CopySpecResolver unroll(StringBuilder path);
    }
}
