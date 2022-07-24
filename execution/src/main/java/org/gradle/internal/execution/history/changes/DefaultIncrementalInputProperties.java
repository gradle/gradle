/*
 * Copyright 2019 the original author or authors.
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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Maps;
import org.gradle.api.InvalidUserDataException;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;

public class DefaultIncrementalInputProperties implements IncrementalInputProperties {
    private final ImmutableBiMap<String, Object> incrementalInputProperties;

    public DefaultIncrementalInputProperties(ImmutableBiMap<String, Object> incrementalInputProperties) {
        this.incrementalInputProperties = incrementalInputProperties;
    }

    @Override
    public String getPropertyNameFor(Object propertyValue) {
        String propertyName = incrementalInputProperties.inverse().get(propertyValue);
        if (propertyName == null) {
            throw new InvalidUserDataException("Cannot query incremental changes: No property found for value " + propertyValue + ". Incremental properties: " + Joiner.on(", ").join(incrementalInputProperties.keySet()) + ".");
        }
        return propertyName;
    }

    @Override
    public InputFileChanges nonIncrementalChanges(ImmutableSortedMap<String, FileCollectionFingerprint> previous, ImmutableSortedMap<String, CurrentFileCollectionFingerprint> current) {
        return new DefaultInputFileChanges(
            Maps.filterKeys(previous, propertyName -> !incrementalInputProperties.containsKey(propertyName)),
            Maps.filterKeys(current, propertyName -> !incrementalInputProperties.containsKey(propertyName))
        );

    }

    @Override
    public InputFileChanges incrementalChanges(ImmutableSortedMap<String, FileCollectionFingerprint> previous, ImmutableSortedMap<String, CurrentFileCollectionFingerprint> current) {
        return new DefaultInputFileChanges(
            ImmutableSortedMap.copyOfSorted(Maps.filterKeys(previous, propertyName -> incrementalInputProperties.containsKey(propertyName))),
            ImmutableSortedMap.copyOfSorted(Maps.filterKeys(current, propertyName -> incrementalInputProperties.containsKey(propertyName)))
        );
    }
}
