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
package org.gradle.api.internal.file.copy;

import com.google.common.collect.ImmutableList;
import org.gradle.api.Action;
import org.gradle.api.tasks.Nested;

public class ResolvedCopySpecNode {
    private final ResolvedCopySpec spec;
    private final ImmutableList<ResolvedCopySpecNode> children;

    public ResolvedCopySpecNode(ResolvedCopySpec spec, ImmutableList<ResolvedCopySpecNode> children) {
        this.spec = spec;
        this.children = children;
    }

    @Nested
    public ResolvedCopySpec getSpec() {
        return spec;
    }

    @Nested
    public ImmutableList<ResolvedCopySpecNode> getChildren() {
        return children;
    }

    public void walk(Action<? super ResolvedCopySpec> action) {
        action.execute(spec);
        for (ResolvedCopySpecNode child : children) {
            child.walk(action);
        }
    }
}
