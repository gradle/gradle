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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy;

import org.gradle.api.internal.FeaturePreviews;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.Versioned;

import java.util.Comparator;

/**
 * This comparator considers `1.1.1 == 1-1-1`.
 * @see StaticVersionComparator
 */
public class DefaultVersionComparator implements VersionComparator {
    private Comparator<Version> baseComparator;
    private final FeaturePreviews featurePreviews;

    public DefaultVersionComparator() {
        // We preserve the no-arg constructor since this change is temporary
        // and there is a chance this class is used in plugins out there
        this(null);
    }
    public DefaultVersionComparator(FeaturePreviews featurePreviews) {
        this.featurePreviews = featurePreviews;
        computeBaseComparator();
    }

    @Override
    public int compare(Versioned element1, Versioned element2) {
        Version version1 = element1.getVersion();
        Version version2 = element2.getVersion();
        return baseComparator.compare(version1, version2);
    }

    @Override
    public Comparator<Version> asVersionComparator() {
        return baseComparator;
    }

    public void configure() {
        computeBaseComparator();
    }

    // To be removed once the feature preview disappears
    private void computeBaseComparator() {
        if (featurePreviews != null && featurePreviews.isFeatureEnabled(FeaturePreviews.Feature.VERSION_ORDERING_V2)) {
            baseComparator = new StaticVersionComparator(StaticVersionComparator.UPDATED_SPECIAL_MEANINGS);
        } else {
            baseComparator = new StaticVersionComparator(StaticVersionComparator.SPECIAL_MEANINGS);
        }
    }
}
