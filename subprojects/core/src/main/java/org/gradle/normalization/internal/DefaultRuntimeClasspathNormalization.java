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

package org.gradle.normalization.internal;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.internal.changedetection.state.IgnoringResourceEntryFilter;
import org.gradle.api.internal.changedetection.state.IgnoringResourceFilter;
import org.gradle.api.internal.changedetection.state.ResourceEntryFilter;
import org.gradle.api.internal.changedetection.state.ResourceFilter;
import org.gradle.internal.fingerprint.classpath.ClasspathResourceFilters;
import org.gradle.normalization.MetaInfNormalization;

import java.util.Locale;

public class DefaultRuntimeClasspathNormalization implements RuntimeClasspathNormalizationInternal {
    private final ImmutableSet.Builder<String> ignoresBuilder = ImmutableSet.builder();
    private final ImmutableSet.Builder<String> attributeIgnoresBuilder = ImmutableSet.builder();
    private final ImmutableSet.Builder<String> propertiesIgnoresBuilder = ImmutableSet.builder();
    private final MetaInfNormalization metaInfNormalization = new RuntimeMetaInfNormalization();

    private boolean evaluated = false;
    private ResourceFilter resourceFilter;
    private ResourceEntryFilter attributeResourceFilter;
    private ResourceEntryFilter propertyResourceFilter;

    @Override
    public void ignore(String pattern) {
        checkNotEvaluated();
        ignoresBuilder.add(pattern);
    }

    @Override
    public void metaInf(Action<? super MetaInfNormalization> configuration) {
        configuration.execute(metaInfNormalization);
    }

    @Override
    public ClasspathResourceFilters getResourceFilters() {
        resourceFilter = maybeCreateFilter(resourceFilter, ignoresBuilder);
        attributeResourceFilter = maybeCreateEntryFilter(attributeResourceFilter, attributeIgnoresBuilder);
        propertyResourceFilter = maybeCreateEntryFilter(propertyResourceFilter, propertiesIgnoresBuilder);
        return new ClasspathResourceFilters() {
            @Override
            public ResourceFilter getResourceFilter() {
                return resourceFilter;
            }

            @Override
            public ResourceEntryFilter getManifestAttributeEntryFilter() {
                return attributeResourceFilter;
            }

            @Override
            public ResourceEntryFilter getManifestPropertyEntryFilter() {
                return propertyResourceFilter;
            }
        };
    }

    private void checkNotEvaluated() {
        if (evaluated) {
            throw new GradleException("Cannot configure runtime classpath normalization after execution started.");
        }
    }

    private ResourceFilter maybeCreateFilter(ResourceFilter existingFilter, ImmutableSet.Builder<String> ignoresBuilder) {
        evaluated = true;
        if (existingFilter == null) {
            ImmutableSet<String> ignores = ignoresBuilder.build();
            return ignores.isEmpty() ? ResourceFilter.FILTER_NOTHING : new IgnoringResourceFilter(ignores);
        }
        return existingFilter;
    }

    private ResourceEntryFilter maybeCreateEntryFilter(ResourceEntryFilter existingEntryFilter, ImmutableSet.Builder<String> ignoresBuilder) {
        evaluated = true;
        if (existingEntryFilter == null) {
            ImmutableSet<String> ignores = ignoresBuilder.build();
            return ignores.isEmpty() ? ResourceEntryFilter.FILTER_NOTHING : new IgnoringResourceEntryFilter(ignores);
        }
        return existingEntryFilter;
    }

    public class RuntimeMetaInfNormalization implements MetaInfNormalization {
        @Override
        public void ignoreCompletely() {
            ignore("META-INF/*");
        }

        @Override
        public void ignoreManifest() {
            ignore("META-INF/MANIFEST.MF");
        }

        @Override
        public void ignoreAttribute(String name) {
            checkNotEvaluated();
            attributeIgnoresBuilder.add(name.toLowerCase(Locale.ROOT));
        }

        @Override
        public void ignoreProperty(String name) {
            checkNotEvaluated();
            propertiesIgnoresBuilder.add(name);
        }
    }
}
