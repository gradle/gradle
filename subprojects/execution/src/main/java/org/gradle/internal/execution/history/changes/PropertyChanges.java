/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.execution.history.changes;

import com.google.common.collect.ImmutableSortedMap;
import org.gradle.api.Describable;

public class PropertyChanges implements ChangeContainer {

    private final ImmutableSortedMap<String, ?> previous;
    private final ImmutableSortedMap<String, ?> current;
    private final String title;
    private final Describable executable;

    // TODO This should actually compare a SortedSet
    protected PropertyChanges(ImmutableSortedMap<String, ?> previous, ImmutableSortedMap<String, ?> current, String title, Describable executable) {
        this.previous = previous;
        this.current = current;
        this.title = title;
        this.executable = executable;
    }

    @Override
    public boolean accept(final ChangeVisitor visitor) {
        return SortedMapDiffUtil.diff(previous, current, new PropertyDiffListener<String, Object, Object>() {
            @Override
            public boolean removed(String previousProperty) {
                return visitor.visitChange(new DescriptiveChange("%s property '%s' has been removed for %s",
                        title, previousProperty, executable.getDisplayName()));
            }

            @Override
            public boolean added(String currentProperty) {
                return visitor.visitChange(new DescriptiveChange("%s property '%s' has been added for %s",
                        title, currentProperty, executable.getDisplayName()));
            }

            @Override
            public boolean updated(String property, Object previous, Object current) {
                // Ignore
                return true;
            }
        });
    }
}
