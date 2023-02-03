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

package org.gradle.language.nativeplatform;

import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.nativeplatform.tasks.LinkExecutable;

/**
 * Represents a native component that produces an executable.
 *
 * @since 4.5
 */
public interface ComponentWithExecutable extends ComponentWithNativeRuntime {
    /**
     * Returns the link libraries to use to link the executable. Includes the link libraries of the component's dependencies.
     */
    FileCollection getLinkLibraries();

    /**
     * Returns the task that should be run to produce the executable file of this component. This isn't necessarily the link task for the component.
     *
     * @since 5.1
     */
    Provider<? extends Task> getExecutableFileProducer();

    /**
     * Returns the executable file to produce.
     */
    Provider<RegularFile> getExecutableFile();

    /**
     * Returns the link task for the executable.
     */
    Provider<? extends LinkExecutable> getLinkTask();
}
