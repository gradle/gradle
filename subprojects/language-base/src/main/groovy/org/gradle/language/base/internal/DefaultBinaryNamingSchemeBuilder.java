/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.language.base.internal;

import java.util.ArrayList;
import java.util.List;

public class DefaultBinaryNamingSchemeBuilder implements BinaryNamingSchemeBuilder {
    private final String parentName;
    private final String typeString;
    private final List<String> dimensions;

    public DefaultBinaryNamingSchemeBuilder() {
        this.parentName = null;
        this.typeString = "";
        this.dimensions = new ArrayList<String>();
    }

    public DefaultBinaryNamingSchemeBuilder(BinaryNamingScheme basis) {
        assert basis instanceof DefaultBinaryNamingScheme;
        DefaultBinaryNamingScheme clone = (DefaultBinaryNamingScheme) basis;
        this.parentName = clone.parentName;
        this.typeString = clone.typeString;
        this.dimensions = clone.dimensions;
    }

    private DefaultBinaryNamingSchemeBuilder(String parentName, String typeString, List<String> dimensions) {
        this.parentName = parentName;
        this.typeString = typeString;
        this.dimensions = dimensions;
    }

    public BinaryNamingSchemeBuilder withComponentName(String name) {
        return new DefaultBinaryNamingSchemeBuilder(name, typeString, dimensions);
    }

    public BinaryNamingSchemeBuilder withTypeString(String newTypeString) {
        return new DefaultBinaryNamingSchemeBuilder(parentName, newTypeString, dimensions);
    }

    public BinaryNamingSchemeBuilder withVariantDimension(String dimension) {
        List<String> newDimensions = new ArrayList<String>(dimensions);
        newDimensions.add(dimension);
        return new DefaultBinaryNamingSchemeBuilder(parentName, typeString, newDimensions);
    }

    public BinaryNamingScheme build() {
        return new DefaultBinaryNamingScheme(parentName, typeString, dimensions);
    }
}
