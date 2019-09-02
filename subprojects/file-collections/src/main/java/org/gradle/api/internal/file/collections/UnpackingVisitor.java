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

package org.gradle.api.internal.file.collections;

import org.gradle.api.Buildable;
import org.gradle.api.Task;
import org.gradle.api.file.DirectoryTree;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.provider.ProviderInternal;
import org.gradle.api.internal.tasks.TaskDependencyContainer;
import org.gradle.api.tasks.TaskOutputs;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.util.DeferredUtil;

import javax.annotation.Nullable;
import java.nio.file.Path;

public class UnpackingVisitor {
    private final FileCollectionResolveContext context;
    private final PathToFileResolver resolver;

    public UnpackingVisitor(FileCollectionResolveContext context, PathToFileResolver resolver) {
        this.context = context;
        this.resolver = resolver;
    }

    public void add(@Nullable Object element) {
        if (element instanceof FileCollection) {
            // FileCollection is-a Iterable, Buildable and TaskDependencyContainer, so check before checking for these things
            context.add(element);
            return;
        }
        if (element instanceof DirectoryTree) {
            context.add(element);
            return;
        }
        if (element instanceof ProviderInternal) {
            // ProviderInternal is-a TaskDependencyContainer, so check first
            ProviderInternal provider = (ProviderInternal) element;
            if (!context.maybeAdd(provider)) {
                // Unpack the provider
                add(provider.get());
            }
            return;
        }

        // Elements that may or may not be interesting only for build dependency calculation
        if (element instanceof Buildable || element instanceof TaskDependencyContainer) {
            if (context.maybeAdd(element)) {
                return;
            }
            // Else, continue below
        }

        if (element instanceof Task) {
            context.add(((Task) element).getOutputs().getFiles());
        } else if (element instanceof TaskOutputs) {
            context.add(((TaskOutputs) element).getFiles());
        } else if (DeferredUtil.isNestableDeferred(element)) {
            Object deferredResult = DeferredUtil.unpackNestableDeferred(element);
            if (deferredResult != null) {
                add(deferredResult);
            }
        } else if (element instanceof Path) {
            // Path is-a Iterable, so check before checking for Iterable
            context.add(element, resolver);
        } else if (element instanceof Iterable) {
            Iterable<?> iterable = (Iterable) element;
            for (Object item : iterable) {
                add(item);
            }
        } else if (element instanceof Object[]) {
            Object[] array = (Object[]) element;
            for (Object value : array) {
                add(value);
            }
        } else if (element != null) {
            // Treat everything else as a single file
            context.add(element, resolver);
        }
    }
}
