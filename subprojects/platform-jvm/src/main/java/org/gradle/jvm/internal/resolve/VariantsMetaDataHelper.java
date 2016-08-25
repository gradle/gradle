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
package org.gradle.jvm.internal.resolve;

import com.google.common.collect.Sets;
import org.gradle.model.internal.type.ModelType;

import java.util.Set;

public class VariantsMetaDataHelper {
    public static Set<String> determineAxesWithIncompatibleTypes(VariantsMetaData reference, VariantsMetaData candidate, Set<String> testedDimensions) {
        Set<String> result = Sets.newHashSet();
        for (String commonDimension : testedDimensions) {
            ModelType<?> resolveType = reference.getVariantAxisType(commonDimension);
            ModelType<?> binaryVariantType = candidate.getVariantAxisType(commonDimension);
            if (binaryVariantType != null && !resolveType.isAssignableFrom(binaryVariantType)) {
                result.add(commonDimension);
            }
        }
        return result;
    }
}
