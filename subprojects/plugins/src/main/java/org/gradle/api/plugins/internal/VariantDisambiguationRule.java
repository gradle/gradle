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

package org.gradle.api.plugins.internal;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.attributes.AttributeDisambiguationRule;
import org.gradle.api.attributes.MultipleCandidatesDetails;
import org.gradle.api.plugins.JavaPlugin;

import java.util.Set;

public class VariantDisambiguationRule implements AttributeDisambiguationRule<String> {
    private static final Set<String> VARIANT_TYPES = ImmutableSet.of(JavaPlugin.JAR_TYPE, JavaPlugin.CLASS_DIRECTORY, JavaPlugin.RESOURCES_DIRECTORY);
    private static final Set<String> DIR_VARIANT_TYPES = ImmutableSet.of(JavaPlugin.NON_DEFAULT_JAR_TYPE, JavaPlugin.CLASS_DIRECTORY, JavaPlugin.RESOURCES_DIRECTORY);

    @Override
    public void execute(MultipleCandidatesDetails<String> details) {
        // Use Jar if all are selected
        if (details.getCandidateValues().equals(VARIANT_TYPES)) {
            details.closestMatch(JavaPlugin.JAR_TYPE);
            return;
        }
        // Use classes if dir variants are selected
        if (details.getCandidateValues().equals(DIR_VARIANT_TYPES)) {
            details.closestMatch(JavaPlugin.CLASS_DIRECTORY);
        }
    }
}
