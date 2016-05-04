/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.tooling.internal.consumer.converters;

import com.google.common.collect.ImmutableList;
import org.gradle.api.Action;
import org.gradle.tooling.internal.adapter.SourceObjectMapping;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class CompositeCompatibilityMapping implements Action<SourceObjectMapping>, Serializable {

    public static class Builder {
        private final List<Action<SourceObjectMapping>> mappings = new ArrayList<Action<SourceObjectMapping>>();

        public Builder add(Action<SourceObjectMapping> mapping) {
            mappings.add(mapping);
            return this;
        }

        public CompositeCompatibilityMapping build() {
            for (Action<SourceObjectMapping> mapping : mappings) {
                if (!(mapping instanceof Serializable)) {
                    throw new IllegalArgumentException(String.format("Source object mapping '%s' must be serializable", mapping));
                }
            }
            return new CompositeCompatibilityMapping(mappings);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    final List<Action<SourceObjectMapping>> mappings;

    private CompositeCompatibilityMapping(List<Action<SourceObjectMapping>> mappings) {
        this.mappings = ImmutableList.copyOf(mappings);
    }

    @Override
    public void execute(SourceObjectMapping sourceObjectMapping) {
        for (Action<SourceObjectMapping> mapping : mappings) {
            mapping.execute(sourceObjectMapping);
        }
    }
}
