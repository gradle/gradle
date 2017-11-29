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
package org.gradle.api.internal.artifacts.repositories;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.gradle.api.GradleException;
import org.gradle.caching.internal.BuildCacheHasher;
import org.gradle.internal.Cast;
import org.gradle.internal.reflect.Instantiator;

import java.util.List;

public class DefaultMetadataSources implements MetadataSourcesInternal {
    private final List<Class<? extends MetadataSource>> metadataSources = Lists.newArrayListWithExpectedSize(3);

    public static MetadataSourcesInternal ivyDefaults() {
        DefaultMetadataSources metadataSources = new DefaultMetadataSources();
        metadataSources.gradleMetadata();
        metadataSources.ivyDescriptor();
        metadataSources.artifact();
        return metadataSources;
    }

    public static MetadataSourcesInternal mavenDefaults() {
        DefaultMetadataSources sources = new DefaultMetadataSources();
        sources.gradleMetadata();
        sources.mavenPom();
        sources.artifact();
        return sources;
    }

    @Override
    public void reset() {
        metadataSources.clear();
    }

    @Override
    public ImmutableMetadataSources asImmutable(Instantiator instantiator) {
        return new DefaultImmutableMetadataSources(instantiator, metadataSources);
    }

    @Override
    public void using(Class<? extends MetadataSource> metadataSource) {
        metadataSources.add(metadataSource);
    }

    @Override
    public void gradleMetadata() {
        using(DefaultGradleModuleMetadataSource.class);
    }

    @Override
    public void ivyDescriptor() {
        using(DefaultIvyDescriptorMetadataSource.class);
    }

    @Override
    public void mavenPom() {
        using(DefaultMavenPomMetadataSource.class);
    }

    @Override
    public void artifact() {
        using(DefaultArtifactMetadataSource.class);
    }

    private static class DefaultImmutableMetadataSources implements ImmutableMetadataSources {
        private final ImmutableList<MetadataSource<?>> sources;

        private DefaultImmutableMetadataSources(Instantiator instantiator, List<Class<? extends MetadataSource>> sourceTypes) {
            ImmutableList.Builder<MetadataSource<?>> builder = new ImmutableList.Builder<MetadataSource<?>>();
            ClassLoader classLoader = this.getClass().getClassLoader();
            for (Class<? extends MetadataSource> sourceType : sourceTypes) {
                builder.add(instantiator.newInstance(sourceType));
            }
            this.sources = builder.build();
        }

        // TODO CC: It would be nicer to have a way to locate the concrete type without having to hardcode the location
        // and without having to annotate the public type
        private static Class<? extends MetadataSource<?>> concreteTypeFor(Class<? extends MetadataSource> publicType, ClassLoader classLoader) {
            String fqn = "org.gradle.api.internal.artifacts.repositories.Default" + publicType.getSimpleName();
            try {
                return Cast.uncheckedCast(classLoader.loadClass(fqn));
            } catch (ClassNotFoundException e) {
                throw new GradleException("Unable to load public type for " + publicType.getName(), e);
            }
        }

        @Override
        public ImmutableList<MetadataSource<?>> sources() {
            return sources;
        }

        @Override
        public void appendId(BuildCacheHasher hasher) {
            hasher.putInt(sources.size());
            for (MetadataSource<?> source : sources) {
                source.appendId(hasher);
            }
        }
    }
}
