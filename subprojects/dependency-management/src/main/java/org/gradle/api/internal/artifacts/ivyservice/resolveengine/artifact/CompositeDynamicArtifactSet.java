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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact;

import com.google.common.collect.ImmutableList;
import org.gradle.api.Transformer;
import org.gradle.util.CollectionUtils;

import java.util.Collection;
import java.util.List;

public class CompositeDynamicArtifactSet extends CompositeArtifactSet implements DynamicResolvedArtifactSet {
    private CompositeDynamicArtifactSet(List<ResolvedArtifactSet> sets) {
        super(sets);
    }

    public static DynamicResolvedArtifactSet create(Collection<DynamicResolvedArtifactSet> sets) {
        if (sets.size() == 1) {
            return sets.iterator().next();
        }
        return new CompositeDynamicArtifactSet(ImmutableList.<ResolvedArtifactSet>copyOf(sets));
    }
    @Override
    public ResolvedArtifactSet snapshot() {
        return CompositeArtifactSet.of(CollectionUtils.collect(sets, new Transformer<ResolvedArtifactSet, ResolvedArtifactSet>() {
            @Override
            public ResolvedArtifactSet transform(ResolvedArtifactSet resolvedArtifactSet) {
                return ((DynamicResolvedArtifactSet) resolvedArtifactSet).snapshot();
            }
        }));
    }
}
