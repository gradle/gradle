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
import org.apache.commons.lang.StringUtils;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

/**
 * A memory-efficient builder for variant names.
 *
 * When a component has multiple selected nodes (or multiple selected configurations), we build a name for the variant
 * from the names of the configurations. The set of names for these configurations is relatively limited, so we cache
 * the generated names to avoid recreating multiple times.
 */
public class VariantNameBuilder {
    private final Map<List<String>, DisplayName> names = Maps.newHashMap();

    @Nullable
    public DisplayName getVariantName(@Nullable List<String> parts) {
        if (parts == null) {
            return null;
        }

        DisplayName displayName = names.get(parts);
        if (displayName == null) {
            displayName = variantName(parts);
            names.put(parts, displayName);
        }

        return displayName;
    }

    private static DisplayName variantName(List<String> parts) {
        if (parts.size() == 1) {
            return Describables.of(parts.get(0));
        }
        return new MultipleVariantName(parts);
    }

    private static class MultipleVariantName implements DisplayName {
        private final List<String> parts;

        private MultipleVariantName(List<String> parts) {
            this.parts = parts;
        }

        @Override
        public String getCapitalizedDisplayName() {
            return StringUtils.capitalize(getDisplayName());
        }

        @Override
        public String getDisplayName() {
            StringBuilder sb = new StringBuilder(16 * parts.size());
            boolean appendPlus = false;
            for (String part : parts) {
                if (appendPlus) {
                    sb.append("+");
                }
                sb.append(part);
                appendPlus = true;
            }
            return sb.toString();
        }
    }
}
