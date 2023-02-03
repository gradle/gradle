/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.internal.provider.sources;

import com.google.common.collect.ImmutableMap;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.stream.Stream;

public abstract class MapWithPrefixedKeysValueSource<P extends MapWithPrefixedKeysValueSource.Parameters> implements ValueSource<Map<String, String>, P> {
    public interface Parameters extends ValueSourceParameters {
        Property<String> getPrefix();
    }

    @Nullable
    @Override
    public Map<String, String> obtain() {
        String prefix = getParameters().getPrefix().getOrElse("");

        return itemsToFilter()
            .filter(e -> e.getKey().startsWith(prefix))
            .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    protected abstract Stream<Map.Entry<String, String>> itemsToFilter();
}
