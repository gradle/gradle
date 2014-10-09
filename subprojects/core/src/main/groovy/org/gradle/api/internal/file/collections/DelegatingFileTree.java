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
package org.gradle.api.internal.file.collections;

import groovy.lang.Closure;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.tasks.util.PatternFilterable;

/**
 * A file tree that delegates each method call to the
 * file tree returned by {@link #getDelegate()}.
 */
public abstract class DelegatingFileTree extends DelegatingFileCollection implements FileTree {
    public abstract FileTree getDelegate();

    public FileTree matching(Closure filterConfigClosure) {
        return getDelegate().matching(filterConfigClosure);
    }

    public FileTree matching(PatternFilterable patterns) {
        return getDelegate().matching(patterns);
    }

    public FileTree visit(FileVisitor visitor) {
        return getDelegate().visit(visitor);
    }

    public FileTree visit(Closure visitor) {
        return getDelegate().visit(visitor);
    }

    public FileTree plus(FileTree fileTree) {
        return getDelegate().plus(fileTree);
    }

    public FileTree getAsFileTree() {
        return getDelegate().getAsFileTree();
    }
}
