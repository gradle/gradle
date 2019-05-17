/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.internal.resolve.result;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import org.gradle.internal.resource.ExternalResourceName;

import java.util.List;
import java.util.Set;

public class DefaultResourceAwareResolveResult implements ResourceAwareResolveResult {
    private final Set<String> attempted = Sets.newLinkedHashSet();

    @Override
    public List<String> getAttempted() {
        return ImmutableList.copyOf(attempted);
    }

    @Override
    public void attempted(String locationDescription) {
        attempted.add(locationDescription);
    }

    @Override
    public void attempted(ExternalResourceName location) {
        attempted(location.getDisplayName());
    }

    @Override
    public void applyTo(ResourceAwareResolveResult target) {
        for (String location : attempted) {
            target.attempted(location);
        }
    }
}
