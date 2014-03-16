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

package org.gradle.api.internal.tasks.compile.incremental;

import org.gradle.api.tasks.util.PatternSet;

import java.util.Collection;
import java.util.Collections;

class DefaultRebuildInfo implements RebuildInfo {

    static final RebuildInfo NOTHING_TO_REBUILD = new DefaultRebuildInfo(Collections.<String>emptyList());
    static final RebuildInfo FULL_REBUILD = new DefaultRebuildInfo(null);

    protected final Collection<String> dependentClasses;

    DefaultRebuildInfo(Collection<String> dependentClasses) {
        this.dependentClasses = dependentClasses;
    }

    public Info configureCompilation(PatternSet changedSourceOnly, StaleClassProcessor staleClassProcessor) {
        if (dependentClasses == null) {
            return Info.FullRebuild;
        }
        for (String c : dependentClasses) {
            staleClassProcessor.addStaleClass(c);
            changedSourceOnly.include(c.replaceAll("\\.", "/").concat(".java"));
        }
        return Info.Incremental;
    }
}
