/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.artifacts.configurations;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import org.gradle.api.NonNullApi;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactBackedResolvedVariant;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.CompositeResolvedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet;
import org.gradle.api.internal.artifacts.transform.ArtifactTransformResult;
import org.gradle.api.internal.artifacts.transform.ArtifactTransformTask;
import org.gradle.api.internal.artifacts.transform.ArtifactTransformer;
import org.gradle.api.internal.artifacts.transform.InitialArtifactTransformTask;
import org.gradle.api.internal.artifacts.transform.IntermediateArtifactTransformTask;
import org.gradle.api.internal.project.taskfactory.ITaskFactory;
import org.gradle.util.NameValidator;

import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@NonNullApi
public class ArtifactTransformTaskRegistry {

    private final ConcurrentHashMap<ArtifactTransformTaskKey, ArtifactTransformResult> results = new ConcurrentHashMap<ArtifactTransformTaskKey, ArtifactTransformResult>();
    private final ITaskFactory taskFactory;

    public ArtifactTransformTaskRegistry(ITaskFactory taskFactory) {
        this.taskFactory = taskFactory;
    }

    public void register(ResolvedArtifactSet delegate, ArtifactTransformer transform, ArtifactTransformResult result) {
        results.put(new ArtifactTransformTaskKey(Preconditions.checkNotNull(getArtifactIdentifiers(delegate)), transform), result);
    }

    @Nullable
    public ArtifactTransformResult get(ResolvedArtifactSet delegate, ArtifactTransformer transform) {
        List<ComponentArtifactIdentifier> artifactIdentifiers = getArtifactIdentifiers(delegate);
        if (artifactIdentifiers == null) {
            return null;
        }
        return results.get(new ArtifactTransformTaskKey(artifactIdentifiers, transform));
    }

    public ArtifactTransformTask getOrCreate(ResolvedArtifactSet delegate, ArtifactTransformer transform, @Nullable ArtifactTransformTask innerTask) {
        List<ComponentArtifactIdentifier> artifactIdentifiers = getArtifactIdentifiers(delegate);
        Preconditions.checkNotNull(artifactIdentifiers, "artifact identifiers");
        ArtifactTransformTaskKey key = new ArtifactTransformTaskKey(artifactIdentifiers, transform);
        if (!results.containsKey(key)) {
            results.putIfAbsent(key, createArtifactTransformTask(transform, delegate, innerTask));
        }
        return (ArtifactTransformTask) results.get(key);
    }

    private ArtifactTransformTask createArtifactTransformTask(ArtifactTransformer transform, ResolvedArtifactSet delegate, @Nullable ArtifactTransformTask innerTransformTask) {
        if (innerTransformTask == null) {
            return taskFactory.create(
                NameValidator.asReallyValidName(Joiner.on("-").join(transform.getDisplayName(), ((ArtifactBackedResolvedVariant.SingleArtifactSet) delegate).getArtifact().getId().getDisplayName(), System.nanoTime())),
                InitialArtifactTransformTask.class,
                transform,
                delegate
            );
        }
        return taskFactory.create(
            NameValidator.asReallyValidName(Joiner.on("-").join(transform.getDisplayName(), ((ArtifactBackedResolvedVariant.SingleArtifactSet) delegate).getArtifact().getId().getDisplayName())),
            IntermediateArtifactTransformTask.class,
            transform,
            innerTransformTask
        );
    }

    @Nullable
    private static List<ComponentArtifactIdentifier> getArtifactIdentifiers(ResolvedArtifactSet artifactSet) {
        ImmutableList.Builder<ComponentArtifactIdentifier> builder = ImmutableList.builder();
        if (artifactSet instanceof CompositeResolvedArtifactSet) {
            for (ResolvedArtifactSet resolvedArtifactSet : ((CompositeResolvedArtifactSet) artifactSet).getSets()) {
                if (resolvedArtifactSet instanceof ArtifactBackedResolvedVariant.SingleArtifactSet) {
                    builder.add(getIdentifier((ArtifactBackedResolvedVariant.SingleArtifactSet) resolvedArtifactSet));
                } else {
                    return null;
                }
            }
        } else if (artifactSet instanceof ArtifactBackedResolvedVariant.SingleArtifactSet) {
            builder.add(getIdentifier((ArtifactBackedResolvedVariant.SingleArtifactSet) artifactSet));
        } else {
            return null;
        }
        return builder.build();
    }

    private static ComponentArtifactIdentifier getIdentifier(ArtifactBackedResolvedVariant.SingleArtifactSet artifactSet) {
        return artifactSet.getArtifact().getId();
    }

    private static class ArtifactTransformTaskKey {
        private final List<ComponentArtifactIdentifier> artifactIdentifiers;
        private final ArtifactTransformer transform;

        private ArtifactTransformTaskKey(List<ComponentArtifactIdentifier> artifactIdentifiers, ArtifactTransformer transform) {
            this.artifactIdentifiers = artifactIdentifiers;
            this.transform = transform;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            ArtifactTransformTaskKey that = (ArtifactTransformTaskKey) o;

            if (!artifactIdentifiers.equals(that.artifactIdentifiers)) {
                return false;
            }
            return transform.equals(that.transform);
        }

        @Override
        public int hashCode() {
            int result = artifactIdentifiers.hashCode();
            result = 31 * result + transform.hashCode();
            return result;
        }
    }
}
