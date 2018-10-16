/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.file;

import groovy.lang.Closure;
import org.gradle.api.Incubating;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;

import java.io.File;

/**
 * Provides access to several important locations for a project.
 *
 * An instance of the factory can be injected into a task or plugin by annotating a public constructor or method with {@code javax.inject.Inject}. It is also available via {@link org.gradle.api.Project#getLayout()}.
 *
 * @since 4.1
 */
@Incubating
public interface ProjectLayout {
    /**
     * Returns the project directory.
     */
    Directory getProjectDirectory();

    /**
     * Returns the build directory for the project.
     */
    DirectoryProperty getBuildDirectory();

    /**
     * Creates a new {@link DirectoryProperty} that uses the project directory to resolve relative paths, if required. The property has no initial value.
     *
     * @since 4.3
     * @deprecated Replaced by {@link ObjectFactory#directoryProperty()}
     */
    @Deprecated
    DirectoryProperty directoryProperty();

    /**
     * Creates a new {@link DirectoryProperty} that uses the project directory to resolve relative paths, if required. The property has the initial provider specified.
     *
     * @param initialProvider initial provider for the property
     * @since 4.4
     * @deprecated Replaced by {@link ObjectFactory#directoryProperty()}
     */
    @Deprecated
    DirectoryProperty directoryProperty(Provider<? extends Directory> initialProvider);

    /**
     * Creates a new {@link RegularFileProperty} that uses the project directory to resolve relative paths, if required. The property has no initial value.
     *
     * @since 4.3
     * @deprecated Replaced by {@link ObjectFactory#fileProperty()}
     */
    @Deprecated
    RegularFileProperty fileProperty();

    /**
     * Creates a new {@link RegularFileProperty} that uses the project directory to resolve relative paths, if required. The property has the initial provider specified.
     *
     * @param initialProvider initial provider for the property
     * @since 4.4
     * @deprecated Replaced by {@link ObjectFactory#fileProperty()}
     */
    @Deprecated
    RegularFileProperty fileProperty(Provider<? extends RegularFile> initialProvider);

    /**
     * Creates a {@link RegularFile} provider whose location is calculated from the given {@link Provider}.
     */
    Provider<RegularFile> file(Provider<File> file);

    /**
     * <p>Creates a {@link FileCollection} for the given targets. You can pass any of the following
     * types to this method:</p>
     *
     * <ul> <li>A {@link CharSequence}, including {@link String} or {@link groovy.lang.GString}. Interpreted relative to the project directory, as per {@link Project#file(Object)}. A string that starts with {@code file:} is treated as a file URL.</li>
     *
     * <li>A {@link File}. Interpreted relative to the project directory, as per {@link Project#file(Object)}.</li>
     *
     * <li>A {@link java.nio.file.Path} as defined by {@link Project#file(Object)}.</li>
     *
     * <li>A {@link java.net.URI} or {@link java.net.URL}. The URL's path is interpreted as a file path. Only {@code file:} URLs are supported.</li>
     *
     * <li>A {@link org.gradle.api.file.Directory} or {@link org.gradle.api.file.RegularFile}.</li>
     *
     * <li>A {@link java.util.Collection}, {@link Iterable}, or an array that contains objects of any supported type. The elements of the collection are recursively converted to files.</li>
     *
     * <li>A {@link org.gradle.api.file.FileCollection}. The contents of the collection are included in the returned collection.</li>
     *
     * <li>A {@link Provider} of any supported type. The provider's value is recursively converted to files. If the provider represents an output of a task, that task is executed if the file collection is used as an input to another task.
     *
     * <li>A {@link java.util.concurrent.Callable} that returns any supported type. The return value of the {@code call()} method is recursively converted to files. A {@code null} return value is treated as an empty collection.</li>
     *
     * <li>A {@link Closure} that returns any of the types listed here. The return value of the closure is recursively converted to files. A {@code null} return value is treated as an empty collection.</li>
     *
     * <li>A {@link Task}. Converted to the task's output files. The task is executed if the file collection is used as an input to another task.</li>
     *
     * <li>A {@link org.gradle.api.tasks.TaskOutputs}. Converted to the output files the related task. The task is executed if the file collection is used as an input to another task.</li>
     *
     * <li>Anything else is treated as a failure.</li>
     *
     * </ul>
     *
     * <p>The returned file collection is lazy, so that the paths are evaluated only when the contents of the file
     * collection are queried. The file collection is also live, so that it evaluates the above each time the contents
     * of the collection is queried.</p>
     *
     * <p>The returned file collection maintains the iteration order of the supplied paths.</p>
     *
     * <p>The returned file collection maintains the details of the tasks that produce the files, so that these tasks are executed if this file collection is used as an input to some task.</p>
     *
     * <p>This method can also be used to create an empty collection, but the collection may not be mutated later.</p>
     *
     * @param  paths The paths to the files. May be empty.
     * @return The file collection. Never returns null.
     * @since 4.8
     */
    FileCollection files(Object... paths);

    /**
     * <p>Returns a {@link ConfigurableFileCollection} containing the given files. You can pass any of the following
     * types to this method:</p>
     *
     * <ul> <li>A {@link CharSequence}, including {@link String} or {@link groovy.lang.GString}. Interpreted relative to the project directory, as per {@link Project#file(Object)}. A string that starts with {@code file:} is treated as a file URL.</li>
     *
     * <li>A {@link File}. Interpreted relative to the project directory, as per {@link Project#file(Object)}.</li>
     *
     * <li>A {@link java.nio.file.Path} as defined by {@link Project#file(Object)}.</li>
     *
     * <li>A {@link java.net.URI} or {@link java.net.URL}. The URL's path is interpreted as a file path. Only {@code file:} URLs are supported.</li>
     *
     * <li>A {@link org.gradle.api.file.Directory} or {@link org.gradle.api.file.RegularFile}.</li>
     *
     * <li>A {@link java.util.Collection}, {@link Iterable}, or an array that contains objects of any supported type. The elements of the collection are recursively converted to files.</li>
     *
     * <li>A {@link org.gradle.api.file.FileCollection}. The contents of the collection are included in the returned collection.</li>
     *
     * <li>A {@link Provider} of any supported type. The provider's value is recursively converted to files. If the provider represents an output of a task, that task is executed if the file collection is used as an input to another task.
     *
     * <li>A {@link java.util.concurrent.Callable} that returns any supported type. The return value of the {@code call()} method is recursively converted to files. A {@code null} return value is treated as an empty collection.</li>
     *
     * <li>A {@link Closure} that returns any of the types listed here. The return value of the closure is recursively converted to files. A {@code null} return value is treated as an empty collection.</li>
     *
     * <li>A {@link Task}. Converted to the task's output files. The task is executed if the file collection is used as an input to another task.</li>
     *
     * <li>A {@link org.gradle.api.tasks.TaskOutputs}. Converted to the output files the related task. The task is executed if the file collection is used as an input to another task.</li>
     *
     * <li>Anything else is treated as a failure.</li>
     *
     * </ul>
     *
     * <p>The returned file collection is lazy, so that the paths are evaluated only when the contents of the file
     * collection are queried. The file collection is also live, so that it evaluates the above each time the contents
     * of the collection is queried.</p>
     *
     * <p>The returned file collection maintains the iteration order of the supplied paths.</p>
     *
     * <p>The returned file collection maintains the details of the tasks that produce the files, so that these tasks are executed if this file collection is used as an input to some task.</p>
     *
     * <p>This method can also be used to create an empty collection, which can later be mutated to add elements.</p>
     *
     * @param paths The paths to the files. May be empty.
     * @return The file collection. Never returns null.
     * @since 4.8
     */
    ConfigurableFileCollection configurableFiles(Object... paths);
}
