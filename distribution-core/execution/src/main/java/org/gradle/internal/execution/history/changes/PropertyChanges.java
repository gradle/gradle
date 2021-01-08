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

import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Sets;
import org.gradle.api.Describable;

import java.util.stream.Stream;

public class PropertyChanges implements ChangeContainer {

    private final ImmutableSortedSet<String> previous;
    private final ImmutableSortedSet<String> current;
    private final String title;
    private final Describable executable;

    public PropertyChanges(
        ImmutableSortedSet<String> previous,
        ImmutableSortedSet<String> current,
        String title,
        Describable executable
    ) {
        this.previous = previous;
        this.current = current;
        this.title = title;
        this.executable = executable;
    }

    @Override
    public boolean accept(ChangeVisitor visitor) {
        if (previous.equals(current)) {
            return true;
        }
        Stream<DescriptiveChange> removedProperties = Sets.difference(previous, current).stream()
            .map(removedProperty -> new DescriptiveChange("%s property '%s' has been removed for %s",
                title, removedProperty, executable.getDisplayName()));
        Stream<DescriptiveChange> addedProperties = Sets.difference(current, previous).stream()
            .map(addedProperty -> new DescriptiveChange("%s property '%s' has been added for %s",
                title, addedProperty, executable.getDisplayName()));
        return Stream.concat(removedProperties, addedProperties)
            .map(visitor::visitChange)
            .filter(shouldContinue -> !shouldContinue)
            .findFirst()
            .orElse(true);
    }
}
