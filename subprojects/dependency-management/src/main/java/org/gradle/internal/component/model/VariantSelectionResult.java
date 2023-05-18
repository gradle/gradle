/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.component.model;

import java.util.List;

public class VariantSelectionResult {
    private final List<? extends VariantGraphResolveState> variants;
    private final boolean selectedByVariantAwareResolution;

    public VariantSelectionResult(List<? extends VariantGraphResolveState> variants, boolean selectedByVariantAwareResolution) {
        this.variants = variants;
        this.selectedByVariantAwareResolution = selectedByVariantAwareResolution;
    }

    public List<? extends VariantGraphResolveState> getVariants() {
        return variants;
    }

    public boolean isSelectedByVariantAwareResolution() {
        return selectedByVariantAwareResolution;
    }
}
