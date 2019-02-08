/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.component;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.gradle.api.ecosystem.Ecosystem;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariantSet;
import org.gradle.internal.exceptions.Contextual;
import org.gradle.util.TextUtil;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Contextual
public class VariantSelectionException extends RuntimeException {
    public VariantSelectionException(String message, Set<Ecosystem> knownEcosystems, Set<Ecosystem> requiredEcosystems) {
        super(amendMessageWithEcosystems(message, knownEcosystems, requiredEcosystems));
    }

    static String amendMessageWithEcosystems(String message, Set<Ecosystem> knownEcosystems, Set<Ecosystem> requiredEcosystems) {
        Set<Ecosystem> missingEcosystems = Sets.difference(requiredEcosystems, knownEcosystems);
        if (missingEcosystems.isEmpty()) {
            return message;
        }
        StringBuilder sb = new StringBuilder(message);
        sb.append(TextUtil.getPlatformLineSeparator());
        sb.append("This error might be fixed by applying ");
        int size = missingEcosystems.size();
        if (size == 1) {
            Ecosystem ecosystem = missingEcosystems.iterator().next();
            sb.append("a plugin understanding the ");
            formatEcosystem(sb, ecosystem);
            sb.append(" ecosystem.");
        } else {
            sb.append("plugins understanding the ");
            List<Ecosystem> sorted = Lists.newArrayList(missingEcosystems);
            Collections.sort(sorted, Comparator.comparing(Ecosystem::getName));
            for (int i = 0; i < size; i++) {
                Ecosystem missingEcosystem = sorted.get(i);
                formatEcosystem(sb, missingEcosystem);
                if (i < size - 2) {
                    sb.append(", ");
                } else if (i == size - 2) {
                    sb.append(" and ");
                }
            }
            sb.append(" ecosystems.");
        }
        return sb.toString();
    }

    private static void formatEcosystem(StringBuilder sb, Ecosystem ecosystem) {
        sb.append("'");
        sb.append(ecosystem.getName());
        sb.append("'");
        String description = ecosystem.getDescription();
        if (description != null) {
            sb.append(" (").append(description).append(")");
        }
    }

    private VariantSelectionException(String message, Throwable cause) {
        super(message, cause);
    }

    public static VariantSelectionException selectionFailed(ResolvedVariantSet producer, Throwable failure) {
        return new VariantSelectionException(String.format("Could not select a variant of %s that matches the consumer attributes.", producer.asDescribable().getDisplayName()), failure);
    }
}
