/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.internal.HasInternalProtocol;

/**
 * Synchronizes the contents of a destination directory with some source directories and files.
 *
 * @since 7.5
 */
@Incubating
@HasInternalProtocol
public interface SyncSpec extends CopySpec {

    /**
     * Returns the filter that defines which files to preserve in the destination directory.
     *
     * @return the filter defining the files to preserve
     */
    @Internal
    PatternFilterable getPreserve();

    /**
     * Configures the filter that defines which files to preserve in the destination directory.
     *
     * @param action Action for configuring the preserve filter
     * @return this
     */
    SyncSpec preserve(Action<? super PatternFilterable> action);

}
