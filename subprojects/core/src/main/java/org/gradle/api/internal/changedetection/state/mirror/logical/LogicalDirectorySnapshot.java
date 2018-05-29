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

package org.gradle.api.internal.changedetection.state.mirror.logical;

import org.gradle.api.internal.changedetection.state.DirContentSnapshot;
import org.gradle.api.internal.changedetection.state.FileContentSnapshot;

import java.util.List;

public class LogicalDirectorySnapshot implements LogicalSnapshot {
    private final String name;
    private final List<LogicalSnapshot> children;

    public LogicalDirectorySnapshot(String name, List<LogicalSnapshot> children) {
        this.name = name;
        this.children = children;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public FileContentSnapshot getContent() {
        return DirContentSnapshot.INSTANCE;
    }

    @Override
    public void accept(HierarchicalSnapshotVisitor visitor) {
        visitor.preVisitDirectory(name);
        for (LogicalSnapshot logicalSnapshot : getChildren()) {
            logicalSnapshot.accept(visitor);
        }
        visitor.postVisitDirectory();
    }

    public List<LogicalSnapshot> getChildren() {
        return children;
    }
}
