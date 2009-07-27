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
package org.gradle.api.internal.file;

import org.gradle.api.file.SourceSet;
import org.gradle.api.tasks.StopActionException;
import groovy.lang.Closure;

import java.util.*;

public class CompositeSourceSet implements SourceSet {
    private final Set<SourceSet> sets;

    public CompositeSourceSet(SourceSet... sets) {
        this(Arrays.asList(sets));
    }

    public CompositeSourceSet(Collection<? extends SourceSet> sets) {
        this.sets = new LinkedHashSet<SourceSet>(sets);
    }

    public Set<SourceSet> getSets() {
        return sets;
    }

    public CompositeSourceSet add(SourceSet sourceSet) {
        sets.add(sourceSet);
        return this;
    }

    public SourceSet stopActionIfEmpty() throws StopActionException {
        for (SourceSet set : sets) {
            try {
                set.stopActionIfEmpty();
                return this;
            } catch (StopActionException e) {
                // Continue
            }
        }
        throw new StopActionException("No source files to operate on.");
    }

    public SourceSet matching(Closure filterConfigClosure) {
        List<SourceSet> filteredSets = new ArrayList<SourceSet>();
        for (SourceSet set : sets) {
            filteredSets.add(set.matching(filterConfigClosure));
        }
        return new CompositeSourceSet(filteredSets);
    }

    public Object addToAntBuilder(Object node, String childNodeName) {
        for (SourceSet set : sets) {
            set.addToAntBuilder(node, childNodeName);
        }
        return this;
    }
}
