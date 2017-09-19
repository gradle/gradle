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

package org.gradle.api.tasks;

import org.gradle.api.Incubating;
import org.gradle.api.file.FileCollection;

import java.util.Map;

/**
 * Describes an input property of a task.
 *
 * @since 4.3
 */
@Incubating
public interface TaskInputPropertyBuilder extends TaskPropertyBuilder, TaskInputs {
    /**
     * Sets whether the task property is optional. If the task property is optional, it means that a value does not have to be
     * specified for the property, but any value specified must meet the validation constraints for the property.
     */
    TaskInputPropertyBuilder optional(boolean optional);

    /**
     * Throws {@link UnsupportedOperationException}.
     *
     * @deprecated Use {@link TaskInputs#getHasInputs()} directly instead.
     */
    @Deprecated
    @Override
    boolean getHasInputs();

    /**
     * Throws {@link UnsupportedOperationException}.
     *
     * @deprecated Use {@link TaskInputs#getFiles()} directly instead.
     */
    @Deprecated
    @Override
    FileCollection getFiles();

    /**
     * Throws {@link UnsupportedOperationException}.
     *
     * @deprecated Use {@link TaskInputs#files(Object...)} directly instead.
     */
    @Deprecated
    @Override
    TaskInputFilePropertyBuilder files(Object... paths);

    /**
     * Throws {@link UnsupportedOperationException}.
     *
     * @deprecated Use {@link TaskInputs#file(Object)} directly instead.
     */
    @Deprecated
    @Override
    TaskInputFilePropertyBuilder file(Object path);

    /**
     * Throws {@link UnsupportedOperationException}.
     *
     * @deprecated Use {@link TaskInputs#dir(Object)} directly instead.
     */
    @Deprecated
    @Override
    TaskInputFilePropertyBuilder dir(Object dirPath);

    /**
     * Throws {@link UnsupportedOperationException}.
     *
     * @deprecated Use {@link TaskInputs#getProperties()} directly instead.
     */
    @Deprecated
    @Override
    Map<String, Object> getProperties();

    /**
     * Throws {@link UnsupportedOperationException}.
     *
     * @deprecated Use {@link TaskInputs#property(String, Object)} directly instead.
     */
    @Deprecated
    @Override
    TaskInputPropertyBuilder property(String name, Object value);

    /**
     * Throws {@link UnsupportedOperationException}.
     *
     * @deprecated Use {@link TaskInputs#properties(Map)} directly instead.
     */
    @Deprecated
    @Override
    TaskInputs properties(Map<String, ?> properties);

    /**
     * Throws {@link UnsupportedOperationException}.
     *
     * @deprecated Use {@link TaskInputs#getHasSourceFiles()} directly instead.
     */
    @Deprecated
    @Override
    boolean getHasSourceFiles();

    /**
     * Throws {@link UnsupportedOperationException}.
     *
     * @deprecated Use {@link TaskInputs#getSourceFiles()} directly instead.
     */
    @Deprecated
    @Override
    FileCollection getSourceFiles();
}
