/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.provider.provenance;

import org.jspecify.annotations.Nullable;

/**
 * Effective provenance retained by an ordinary property.
 *
 * <p>This deliberately is not mutation history. A replacing {@code set} replaces the explicit source,
 * and a convention is shown outside the source trace while an explicit source is selected.</p>
 */
public final class PropertyProvenanceState {
    private @Nullable PropertyProvenanceRecord explicitSource;
    private @Nullable PropertyProvenanceRecord convention;
    private boolean explicitSelected;

    public void explicitSource(PropertyProvenanceRecord source) {
        explicitSource = source;
        explicitSelected = true;
    }

    public void convention(PropertyProvenanceRecord source) {
        convention = source;
    }

    public void selectExplicit() {
        explicitSelected = true;
    }

    public void selectConvention() {
        explicitSelected = false;
    }

    public void discardConvention() {
        convention = null;
    }

    public @Nullable PropertyProvenanceRecord getExplicitSource() {
        return explicitSource;
    }

    public @Nullable PropertyProvenanceRecord getConvention() {
        return convention;
    }

    public boolean isExplicitSelected() {
        return explicitSelected;
    }
}
