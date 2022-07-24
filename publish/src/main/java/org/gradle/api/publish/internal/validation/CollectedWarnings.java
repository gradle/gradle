/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.publish.internal.validation;

import com.google.common.collect.Sets;

import java.util.Set;

public class CollectedWarnings {
    private Set<String> unsupportedUsages = null;
    private Set<String> incompatibleUsages = null;
    private Set<String> variantUnsupported = null;

    public void addUnsupported(String text) {
        if (unsupportedUsages == null) {
            unsupportedUsages = Sets.newHashSet();
        }
        unsupportedUsages.add(text);
    }

    public void addIncompatible(String text) {
        if (incompatibleUsages == null) {
            incompatibleUsages = Sets.newHashSet();
        }
        incompatibleUsages.add(text);
    }

    public Set<String> getVariantUnsupported() {
        return variantUnsupported;
    }

    public Set<String> getUnsupportedUsages() {
        return unsupportedUsages;
    }

    public Set<String> getIncompatibleUsages() {
        return incompatibleUsages;
    }

    public boolean isEmpty() {
        return incompatibleUsages == null && unsupportedUsages == null && variantUnsupported == null;
    }

    public void addVariantUnsupported(String text) {
        if (variantUnsupported == null) {
            variantUnsupported = Sets.newHashSet();
        }
        variantUnsupported.add(text);
    }
}
