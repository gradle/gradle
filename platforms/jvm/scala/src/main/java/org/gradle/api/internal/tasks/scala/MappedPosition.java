/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.internal.tasks.scala;

import xsbti.Position;

import java.io.File;
import java.util.Optional;

public class MappedPosition implements Position {
    private final Position delegate;

    public MappedPosition(final Position delegate) {
        this.delegate = delegate;
    }

    @Override
    public Optional<Integer> line() {
        return delegate.line();
    }

    @Override
    public String lineContent() {
        return delegate.lineContent();
    }

    @Override
    public Optional<Integer> offset() {
        return delegate.offset();
    }

    @Override
    public Optional<Integer> pointer() {
        return delegate.pointer();
    }

    @Override
    public Optional<String> pointerSpace() {
        return delegate.pointerSpace();
    }

    @Override
    public Optional<String> sourcePath() {
        return delegate.sourcePath();
    }

    @Override
    public Optional<File> sourceFile() {
        return delegate.sourceFile();
    }

    @Override
    public Optional<Integer> startOffset() {
        return delegate.startOffset();
    }

    @Override
    public Optional<Integer> endOffset() {
        return delegate.endOffset();
    }

    @Override
    public Optional<Integer> startLine() {
        return delegate.startLine();
    }

    @Override
    public Optional<Integer> startColumn() {
        return delegate.startColumn();
    }

    @Override
    public Optional<Integer> endLine() {
        return delegate.endLine();
    }

    @Override
    public Optional<Integer> endColumn() {
        return delegate.endColumn();
    }

    @Override
    public String toString() {
        final Optional<String> sourcePath = this.sourcePath();
        final Optional<Integer> line = this.line();
        final Optional<Integer> column = this.pointer();

        if (sourcePath.isPresent() && line.isPresent() && column.isPresent()) {
            return sourcePath.get() + ":" + line.get() + ":" + (column.get() + 1);
        } else if (sourcePath.isPresent() && line.isPresent()) {
            return sourcePath.get() + ":" + line.get();
        } else {
            return sourcePath.orElse("");
        }
    }
}
