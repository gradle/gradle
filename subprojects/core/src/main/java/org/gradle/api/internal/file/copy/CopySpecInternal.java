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
import org.gradle.api.file.CopySpec;

public interface CopySpecInternal extends CopySpec {

    //TODO - does this belong here or on the resolver? PEZ
    boolean hasSource();

    Iterable<CopySpecInternal> getChildren();

    CopySpecInternal addChild();

    CopySpecInternal addChildBeforeSpec(CopySpecInternal childSpec);

    CopySpecInternal addFirst();

    void walk(Action<? super CopySpecResolver> action);

    CopySpecResolver buildRootResolver();

    CopySpecResolver buildResolverRelativeToParent(CopySpecResolver parent);

    void addChildSpecListener(ChildSpecListener listener);

    void visit(ChildSpecAddress parentPath, ChildSpecVisitor visitor);

    /**
     * Listener triggered when a spec is added to the hierarchy.
     */
    interface ChildSpecListener {
        void childSpecAdded(ChildSpecAddedEvent event);
    }

    /**
     * A visitor to traverse the spec hierarchy.
     */
    interface ChildSpecVisitor {
        void visit(ChildSpecAddress address, CopySpecInternal spec);
    }

    /**
     * The address of a spec relative to its parent.
     */
    interface ChildSpecAddress {
        ChildSpecAddress getParent();

        CopySpecInternal getSpec();

        int getAdditionIndex();

        ChildSpecAddress append(CopySpecInternal spec, int additionIndex);

        ChildSpecAddress append(ChildSpecAddress relativeAddress);

        CopySpecResolver unroll(StringBuilder path);
    }

    /**
     * An event describing a spec being added at the given path in a spec hierarchy.
     */
    class ChildSpecAddedEvent {
        private final ChildSpecAddress path;
        private final CopySpecInternal spec;

        public ChildSpecAddedEvent(ChildSpecAddress path, CopySpecInternal spec) {
            this.path = path;
            this.spec = spec;
        }

        public ChildSpecAddress getPath() {
            return path;
        }

        public CopySpecInternal getSpec() {
            return spec;
        }

        @Override
        public String toString() {
            return path.toString();
        }
    }
}
