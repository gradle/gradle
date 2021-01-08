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

package org.gradle.api.internal.attributes;

import org.gradle.api.Action;
import org.gradle.api.attributes.CompatibilityCheckDetails;
import org.gradle.api.attributes.MultipleCandidatesDetails;

import java.util.Comparator;

public abstract class AttributeMatchingRules {
    public static <T> Action<? super CompatibilityCheckDetails<T>> orderedCompatibility(Comparator<? super T> comparator, boolean reverse) {
        return new DefaultOrderedCompatibilityRule<>(comparator, reverse);
    }

    public static <T> Action<? super MultipleCandidatesDetails<T>> orderedDisambiguation(Comparator<? super T> comparator, boolean pickFirst) {
        return new DefaultOrderedDisambiguationRule<>(comparator, pickFirst);
    }
}
