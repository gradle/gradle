/*
 * Copyright 2016 the original author or authors.
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
import org.gradle.internal.HasInternalProtocol;

import java.util.Map;

/**
 * Describes an input property of a task that contains zero or more files.
 *
 * @since 3.0
 */
@Incubating
@HasInternalProtocol
public interface TaskInputFilePropertyBuilder extends TaskFilePropertyBuilder, TaskInputs {
    /**
     * {@inheritDoc}
     */
    @Override
    TaskInputFilePropertyBuilder withPropertyName(String propertyName);

    /**
     * Skip executing the task if the property contains no files.
     * If there are multiple properties with {code skipWhenEmpty = true}, then they all need to be empty for the task to be skipped.
     */
    TaskInputFilePropertyBuilder skipWhenEmpty();

    /**
     * Sets whether executing the task should be skipped if the property contains no files.
     * If there are multiple properties with {code skipWhenEmpty = true}, then they all need to be empty for the task to be skipped.
     */
    TaskInputFilePropertyBuilder skipWhenEmpty(boolean skipWhenEmpty);

    /**
     * Marks a task property as optional. This means that a value does not have to be specified for the property, but any
     * value specified must meet the validation constraints for the property.
     */
    TaskInputFilePropertyBuilder optional();

    /**
     * Sets whether the task property is optional. If the task property is optional, it means that a value does not have to be
     * specified for the property, but any value specified must meet the validation constraints for the property.
     */
    TaskInputFilePropertyBuilder optional(boolean optional);

    /**
     * Sets the order of the files to be relevant when observing this property.
     *
     * @since 3.1
     *
     * @deprecated This method will be removed in Gradle 4.0 without replacement.
     */
    @Deprecated
    TaskInputFilePropertyBuilder orderSensitive();

    /**
     * Sets whether the order of the files is relevant when observing this property.
     *
     * @since 3.1
     *
     * @deprecated This method will be removed in Gradle 4.0 without replacement.
     */
    @Deprecated
    TaskInputFilePropertyBuilder orderSensitive(boolean orderSensitive);

    /**
     * {@inheritDoc}
     *
     * @since 3.1
     */
    @Override
    TaskInputFilePropertyBuilder withPathSensitivity(PathSensitivity sensitivity);

    /**
     * {@inheritDoc}
     *
     * @deprecated Use {@link TaskInputs#getHasInputs()} directly instead.
     */
    @Deprecated
    @Override
    boolean getHasInputs();

    /**
     * {@inheritDoc}
     *
     * @deprecated Use {@link TaskInputs#getFiles()} directly instead.
     */
    @Deprecated
    @Override
    FileCollection getFiles();

    /**
     * {@inheritDoc}
     *
     * @deprecated Use {@link TaskInputs#files(Object...)} directly instead.
     */
    @Deprecated
    @Override
    TaskInputFilePropertyBuilder files(Object... paths);

    /**
     * {@inheritDoc}
     *
     * @deprecated Use {@link TaskInputs#file(Object)} directly instead.
     */
    @Deprecated
    @Override
    TaskInputFilePropertyBuilder file(Object path);

    /**
     * {@inheritDoc}
     *
     * @deprecated Use {@link TaskInputs#dir(Object)} directly instead.
     */
    @Deprecated
    @Override
    TaskInputFilePropertyBuilder dir(Object dirPath);

    /**
     * {@inheritDoc}
     *
     * @deprecated Use {@link TaskInputs#getProperties()} directly instead.
     */
    @Deprecated
    @Override
    Map<String, Object> getProperties();

    /**
     * {@inheritDoc}
     *
     * @deprecated Use {@link TaskInputs#property(String, Object)} directly instead.
     */
    @Deprecated
    @Override
    TaskInputs property(String name, Object value);

    /**
     * {@inheritDoc}
     *
     * @deprecated Use {@link TaskInputs#properties(Map)} directly instead.
     */
    @Deprecated
    @Override
    TaskInputs properties(Map<String, ?> properties);

    /**
     * {@inheritDoc}
     *
     * @deprecated Use {@link TaskInputs#getHasSourceFiles()} directly instead.
     */
    @Deprecated
    @Override
    boolean getHasSourceFiles();

    /**
     * {@inheritDoc}
     *
     * @deprecated Use {@link TaskInputs#getSourceFiles()} directly instead.
     */
    @Deprecated
    @Override
    FileCollection getSourceFiles();

    /**
     * {@inheritDoc}
     *
     * @deprecated Use {@link TaskInputs#source(Object...)} directly instead.
     */
    @Deprecated
    @Override
    TaskInputs source(Object... paths);

    /**
     * {@inheritDoc}
     *
     * @deprecated Use {@link TaskInputs#source(Object)} directly instead.
     */
    @Deprecated
    @Override
    TaskInputs source(Object path);

    /**
     * {@inheritDoc}
     *
     * @deprecated Use {@link TaskInputs#sourceDir(Object)} directly instead.
     */
    @Deprecated
    @Override
    TaskInputs sourceDir(Object path);
}
