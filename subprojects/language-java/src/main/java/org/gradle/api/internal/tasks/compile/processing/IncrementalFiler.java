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

package org.gradle.api.internal.tasks.compile.processing;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.tools.FileObject;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import java.io.IOException;

/**
 * A decorator for the {@link Filer} which ensures that incremental
 * annotation processors don't break the incremental processing contract.
 */
public class IncrementalFiler implements Filer {
    private final Filer delegate;
    private final IncrementalProcessingStrategy strategy;

    IncrementalFiler(Filer delegate, IncrementalProcessingStrategy strategy) {
        this.delegate = delegate;
        if (strategy == null) {
            throw new NullPointerException("strategy");
        }
        this.strategy = strategy;
    }

    @Override
    public final JavaFileObject createSourceFile(CharSequence name, Element... originatingElements) throws IOException {
        strategy.recordGeneratedType(name, originatingElements);
        return delegate.createSourceFile(name, originatingElements);
    }

    @Override
    public final JavaFileObject createClassFile(CharSequence name, Element... originatingElements) throws IOException {
        strategy.recordGeneratedType(name, originatingElements);
        return delegate.createClassFile(name, originatingElements);
    }

    @Override
    public final FileObject createResource(JavaFileManager.Location location, CharSequence pkg, CharSequence relativeName, Element... originatingElements) throws IOException {
        // Prefer having javac validate the location over us, by calling it first.
        FileObject resource = delegate.createResource(location, pkg, relativeName, originatingElements);
        strategy.recordGeneratedResource(location, pkg, relativeName, originatingElements);
        return resource;
    }

    @Override
    public final FileObject getResource(JavaFileManager.Location location, CharSequence pkg, CharSequence relativeName) throws IOException {
        strategy.recordAccessedResource(location, pkg, relativeName);
        return delegate.getResource(location, pkg, relativeName);
    }
}
