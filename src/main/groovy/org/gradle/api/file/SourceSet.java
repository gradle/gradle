/*
 * Copyright 2009 the original author or authors.
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
import org.gradle.api.tasks.AntBuilderAware;
import org.gradle.api.tasks.StopActionException;

/**
 * A {@code SourceSet} represents a read-only set of source files.
 */
public interface SourceSet extends AntBuilderAware {
    /**
     * Throws a {@link StopActionException} if this source set is empty.
     *
     * @return this
     */
    SourceSet stopActionIfEmpty() throws StopActionException;

    /**
     * <p>Restricts the contents of this set to those source files matching the given filter. The filtered set is live,
     * so that any changes to this source set are reflected in the filtered set.</p>
     *
     * <p>The given closure is ued to configure the filter. A {@link org.gradle.api.tasks.util.PatternFilterable} is
     * passed to the closure as it's delegate. Only files which match the specified include patterns will be included in
     * the filtered set. Any files which match the specified exclude patterns will be excluded from the filtered
     * set.</p>
     *
     * @param filterConfigClosure the closure to use to configure the filter.
     * @return The source set.
     */
    SourceSet matching(Closure filterConfigClosure);
}
