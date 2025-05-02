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

import org.gradle.internal.HasInternalProtocol;

/**
 * Describes an input property of a task that contains zero or more files.
 *
 * @since 3.0
 */
@HasInternalProtocol
public interface TaskInputFilePropertyBuilder extends TaskFilePropertyBuilder {
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
     * Sets which part of the path of files should be considered during up-to-date checks and build cache key calculations.
     *
     * @since 3.1
     */
    TaskInputFilePropertyBuilder withPathSensitivity(PathSensitivity sensitivity);

    /**
     * Sets the normalizer to use for this property.
     *
     * @since 4.3
     */
    TaskInputFilePropertyBuilder withNormalizer(Class<? extends FileNormalizer> normalizer);

    /**
     * Ignore directories during up-to-date checks and build cache key calculations.  When this is set, only the contents of directories
     * will be considered, but not the directories themselves.  Changes to empty directories, and directories that
     * contain only empty directories, will be ignored.
     *
     * @since 6.8
     */
    TaskInputFilePropertyBuilder ignoreEmptyDirectories();

    /**
     * Sets whether directories should be considered during up-to-date checks and build cache key calculations.  Defaults to false.
     *
     * See {@link #ignoreEmptyDirectories()}.
     *
     * @since 6.8
     */
    TaskInputFilePropertyBuilder ignoreEmptyDirectories(boolean ignoreEmptyDirectories);

    /**
     * Normalize line endings in text files during up-to-date checks and build cache key calculations. This setting will have no effect on binary files.
     *
     * <p>
     * Line ending normalization is only supported with ASCII encoding and its supersets (i.e.
     * UTF-8, ISO-8859-1, etc). Other encodings (e.g. UTF-16) will be treated as binary files
     * and will not be subject to line ending normalization.
     * </p>
     *
     * @since 7.2
     */
    TaskInputFilePropertyBuilder normalizeLineEndings();

    /**
     * Sets whether line endings should be normalized during up-to-date checks and build cache key calculations. Defaults to false.
     *
     * See {@link #normalizeLineEndings()}.
     *
     * @param normalizeLineEndings whether line endings should be normalized
     * @since 7.2
     */
    TaskInputFilePropertyBuilder normalizeLineEndings(boolean normalizeLineEndings);
}
