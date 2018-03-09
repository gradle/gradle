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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder;

import com.google.common.collect.Maps;
import org.gradle.internal.Pair;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * A memory-efficient builder for variant names.
 *
 * When a component has multiple selected nodes (or multiple selected configurations), we build a name for the variant
 * from the names of the configurations. The set of names for these configurations is relatively limited, so we cache
 * the generated names to avoid recreating multiple times.
 */
public class VariantNameBuilder {
    private final Map<Pair<String, String>, String> names = Maps.newHashMap();

    public String getVariantName(@Nullable String baseName, String part) {
        if (baseName == null) {
            return part;
        }

        Pair<String, String> key = Pair.of(baseName, part);
        String name = names.get(key);
        if (name == null) {
            name = baseName + "+" + part;
            names.put(key, name);
        }
        return name;
    }
}
