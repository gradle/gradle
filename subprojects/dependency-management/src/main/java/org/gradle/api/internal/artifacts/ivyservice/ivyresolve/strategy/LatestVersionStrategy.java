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

import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.Versioned;
import org.gradle.util.CollectionUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class LatestVersionStrategy implements LatestStrategy {
    private static final Comparator<String> STATIC_VERSION_COMPARATOR = new StaticVersionComparator();

    public <T extends Versioned> List<T> sort(Collection<T> versions) {
        return CollectionUtils.sort(versions, this);
    }

    public <T extends Versioned> T findLatest(Collection<T> elements) {
        return Collections.max(elements, this);
    }

    public int compare(Versioned element1, Versioned element2) {
        String version1 = element1.getVersion();
        String version2 = element2.getVersion();
        return STATIC_VERSION_COMPARATOR.compare(version1, version2);
    }
}
