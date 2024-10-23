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

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.CompositeFileCollection;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.tasks.DefaultTaskDependencyFactory;
import org.gradle.api.internal.tasks.TaskDependencyFactory;

import java.util.function.Consumer;

/**
 * A {@link FileCollection} whose contents is created lazily.
 */
public abstract class LazilyInitializedFileCollection extends CompositeFileCollection {

    // Used in a third-party plugin Freefair AspectJ:
    //https://github.com/freefair/gradle-plugins/blob/fe992e9e8a3ed812c941aae02a594c8094da053c/aspectj-plugin/src/main/java/io/freefair/gradle/plugins/aspectj/internal/AspectJRuntime.java#L49
    // TODO Remove once the third-party usage is considered obsolete.
    /**
     * @deprecated Use the overload accepting the TaskDependencyFactory
     */
    @Deprecated
    @SuppressWarnings("InlineMeSuggester")
    public LazilyInitializedFileCollection() {
        this(DefaultTaskDependencyFactory.withNoAssociatedProject());
    }

    public LazilyInitializedFileCollection(TaskDependencyFactory taskDependencyFactory) {
        super(taskDependencyFactory);
    }

    private FileCollectionInternal delegate;

    @Override
    protected void visitChildren(Consumer<FileCollectionInternal> visitor) {
        if (delegate == null) {
            delegate = (FileCollectionInternal) createDelegate();
        }
        visitor.accept(delegate);
    }

    public abstract FileCollection createDelegate();
}
