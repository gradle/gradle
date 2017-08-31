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

import groovy.lang.Closure;
import org.gradle.api.Incubating;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.specs.Spec;
import org.gradle.internal.HasInternalProtocol;

/**
 * Describes an output property of a task that contains zero or more files.
 *
 * @since 3.0
 */
@Incubating
@HasInternalProtocol
public interface TaskOutputFilePropertyBuilder extends TaskFilePropertyBuilder, TaskOutputs {
    /**
     * {@inheritDoc}
     */
    @Override
    TaskOutputFilePropertyBuilder withPropertyName(String propertyName);

    /**
     * Marks a task property as optional. This means that a value does not have to be specified for the property, but any
     * value specified must meet the validation constraints for the property.
     */
    TaskOutputFilePropertyBuilder optional();

    /**
     * Sets whether the task property is optional. If the task property is optional, it means that a value does not have to be
     * specified for the property, but any value specified must meet the validation constraints for the property.
     */
    TaskOutputFilePropertyBuilder optional(boolean optional);

    /**
     * Throws {@link UnsupportedOperationException}.
     *
     * @deprecated Use {@link TaskOutputs#upToDateWhen(Closure)} instead.
     */
    @Deprecated
    @Override
    void upToDateWhen(Closure upToDateClosure);

    /**
     * Throws {@link UnsupportedOperationException}.
     *
     * @deprecated Use {@link TaskOutputs#upToDateWhen(Spec)} instead.
     */
    @Deprecated
    @Override
    void upToDateWhen(Spec<? super Task> upToDateSpec);

    /**
     * Throws {@link UnsupportedOperationException}.
     *
     * @deprecated Use {@link TaskOutputs#getHasOutput()} instead.
     */
    @Deprecated
    @Override
    boolean getHasOutput();

    /**
     * Throws {@link UnsupportedOperationException}.
     *
     * @deprecated Use {@link TaskOutputs#getFiles()} instead.
     */
    @Deprecated
    @Override
    FileCollection getFiles();

    /**
     * Throws {@link UnsupportedOperationException}.
     *
     * @deprecated Use {@link TaskOutputs#files(Object...)} instead.
     */
    @Deprecated
    @Override
    TaskOutputFilePropertyBuilder files(Object... paths);

    /**
     * Throws {@link UnsupportedOperationException}.
     *
     * @deprecated Use {@link TaskOutputs#file(Object)} instead.
     */
    @Deprecated
    @Override
    TaskOutputFilePropertyBuilder file(Object path);

    /**
     * Throws {@link UnsupportedOperationException}.
     *
     * @deprecated Use {@link TaskOutputs#dir(Object)} instead.
     */
    @Deprecated
    @Override
    TaskOutputFilePropertyBuilder dir(Object path);
}
