/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.vfs.impl;

import org.gradle.internal.snapshot.FileSystemSnapshotVisitor;

import java.io.File;
import java.util.function.Function;
import java.util.function.Predicate;

public interface Node {
    Node getOrCreateChild(String name, Function<Node, Node> nodeSupplier);
    Node replaceChild(String name, Function<Node, Node> nodeSupplier, Predicate<Node> shouldReplaceExisting);
    void removeChild(String name);
    String getAbsolutePath();
    Type getType();

    void accept(FileSystemSnapshotVisitor visitor);

    default String getChildAbsolutePath(String name) {
        return getAbsolutePath() + File.separatorChar + name;
    }

    void underLock(Runnable action);

    enum Type {
        FILE,
        DIRECTORY,
        MISSING,
        UNKNOWN
    }
}
