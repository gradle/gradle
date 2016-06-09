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

package org.gradle.api.internal.tasks;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.gradle.api.tasks.TaskFilePropertyBuilder;

import java.util.List;
import java.util.Set;

public class FilePropertyContainer<T extends TaskFilePropertySpec & TaskFilePropertyBuilder> {
    private int legacyFilePropertyCounter;
    protected final List<T> fileProperties = Lists.newArrayList();

    protected void collectFileProperties(ImmutableList.Builder<? super T> builder) {
        Set<String> propertyNames = Sets.newHashSetWithExpectedSize(fileProperties.size());
        for (T propertySpec : fileProperties) {
            String propertyName = propertySpec.getPropertyName();
            if (propertyName == null) {
                propertyName = nextLegacyPropertyName();
                propertySpec.withPropertyName(propertyName);
            }
            if (!propertyNames.add(propertyName)) {
                throw new IllegalArgumentException(String.format("Multiple file properties with name '%s'", propertyName));
            }
            builder.add(propertySpec);
        }
    }

    private String nextLegacyPropertyName() {
        return "$" + (++legacyFilePropertyCounter);
    }
}
