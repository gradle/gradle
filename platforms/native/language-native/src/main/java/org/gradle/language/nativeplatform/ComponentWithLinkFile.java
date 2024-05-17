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
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;

/**
 * Represents a native component that produces a file to be used at link time.
 *
 * @since 4.5
 */
public interface ComponentWithLinkFile extends ComponentWithNativeRuntime {
    /**
     * Returns the task that should be run to produce the link file of this component. This isn't necessarily the link task for the component.
     *
     * @since 5.1
     */
    Provider<? extends Task> getLinkFileProducer();

    /**
     * Returns the link file of this component.
     */
    Provider<RegularFile> getLinkFile();
}
