/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.plugins.ide.eclipse.model.internal;

import org.gradle.api.internal.provider.views.ListPropertyListView;
import org.gradle.plugins.ide.api.XmlFileContentMerger;
import org.gradle.plugins.ide.eclipse.model.EclipseWtpFacet;
import org.gradle.plugins.ide.eclipse.model.Facet;
import org.jspecify.annotations.NullMarked;

import javax.inject.Inject;
import java.util.List;

@NullMarked
public abstract class DefaultEclipseWtpFacet extends EclipseWtpFacet implements EclipseWtpFacetInternal {

    @Inject
    public DefaultEclipseWtpFacet(XmlFileContentMerger file) {
        super(file);
    }

    @Override
    public List<Facet> getFacets() {
        return new ListPropertyListView<>(getFacetsProperty());
    }

    @Override
    public void setFacets(List<Facet> facets) {
        this.getFacetsProperty().set(facets);
    }
}
