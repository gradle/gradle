/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.externalresource.local;

import org.gradle.api.specs.Spec;
import org.gradle.util.CollectionUtils;
import org.gradle.util.hash.HashValue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class DefaultLocallyAvailableResourceCandidates implements LocallyAvailableResourceCandidates {

    private final List<LocallyAvailableResource> locallyAvailableResources;

    public DefaultLocallyAvailableResourceCandidates(List<LocallyAvailableResource> locallyAvailableResources) {
        this.locallyAvailableResources = locallyAvailableResources;
    }

    public boolean isNone() {
        return locallyAvailableResources.isEmpty();
    }

    public LocallyAvailableResource findByHashValue(final HashValue hashValue) {
        return CollectionUtils.findFirst(locallyAvailableResources, new Spec<LocallyAvailableResource>() {
            public boolean isSatisfiedBy(LocallyAvailableResource element) {
                return element.getSha1().equals(hashValue);
            }
        });
    }

    public Collection<LocallyAvailableResource> getAll() {
        return new ArrayList<LocallyAvailableResource>(locallyAvailableResources);
    }
}
