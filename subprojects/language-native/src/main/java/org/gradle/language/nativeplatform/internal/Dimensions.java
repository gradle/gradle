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

package org.gradle.language.nativeplatform.internal;

import org.apache.commons.lang.StringUtils;
import org.gradle.api.Named;

import java.util.Collection;
import java.util.Optional;

public class Dimensions {
    public static String createDimensionSuffix(Named dimensionValue, Collection<?> multivalueProperty) {
        if (isDimensionVisible(multivalueProperty)) {
            return StringUtils.capitalize(dimensionValue.getName().toLowerCase());
        }
        return "";
    }

    public static String createDimensionSuffix(Optional<? extends Named> dimensionValue, Collection<?> multivalueProperty) {
        if (dimensionValue.isPresent() && isDimensionVisible(multivalueProperty)) {
            return StringUtils.capitalize(dimensionValue.get().getName().toLowerCase());
        }
        return "";
    }

    public static boolean isDimensionVisible(Collection<?> multivalueProperty) {
        return multivalueProperty.size() > 1;
    }
}
