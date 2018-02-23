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

import org.gradle.api.Named;

public class DefaultVariantAxisCompatibility implements VariantAxisCompatibility<Object> {
    @Override
    public boolean isCompatibleWithRequirement(Object requirement, Object value) {
        if (requirement instanceof String) {
            return requirement.equals(value);
        } else if (requirement instanceof Named) {
            return ((Named) requirement).getName().equals(((Named) value).getName());
        }
        return false;
    }

    @Override
    public boolean betterFit(Object requirement, Object oldValue, Object newValue) {
        return false;
    }
}
