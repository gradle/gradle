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
package org.gradle.api.internal.artifacts.result;

import com.google.common.collect.Sets;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.result.*;
import org.gradle.api.artifacts.result.jvm.JvmLibrary;
import org.gradle.api.artifacts.result.jvm.JvmLibraryJavadocArtifact;
import org.gradle.api.artifacts.result.jvm.JvmLibrarySourcesArtifact;
import org.gradle.api.internal.artifacts.result.jvm.DefaultJvmLibrary;
import org.gradle.api.internal.artifacts.result.jvm.DefaultJvmLibraryJavadocArtifact;
import org.gradle.api.internal.artifacts.result.jvm.DefaultJvmLibrarySourcesArtifact;
import org.gradle.internal.reflect.DirectInstantiator;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.util.CollectionUtils;

import java.util.Set;

// TODO:DAZ Unit tests
public class DefaultArtifactResolutionResult implements ArtifactResolutionResult {
    private final Set<ComponentResult> componentResults;

    public DefaultArtifactResolutionResult(Set<ComponentResult> componentResults) {
        this.componentResults = componentResults;
    }

    public Set<ComponentResult> getComponents() {
        return componentResults;
    }

    public <T extends Component> Set<T> getResolvedComponents(Class<T> type) {
        if (type.isAssignableFrom(JvmLibrary.class)) {
            return (Set<T>) getJvmLibraries();
        }
        throw new IllegalArgumentException("Not a known component type: " + type);
    }

    // TODO:DAZ This should live with the JVM model classes
    private Set<JvmLibrary> getJvmLibraries() {
        Set<JvmLibrary> libraries = Sets.newLinkedHashSet();
        for (ComponentResult componentResult : componentResults) {
            if (componentResult instanceof ResolvedComponentArtifactsResult) {
                Set<JvmLibrarySourcesArtifact> sourcesArtifacts = transform((ResolvedComponentArtifactsResult) componentResult, JvmLibrarySourcesArtifact.class, DefaultJvmLibrarySourcesArtifact.class);
                Set<JvmLibraryJavadocArtifact> javadocArtifacts = transform((ResolvedComponentArtifactsResult) componentResult, JvmLibraryJavadocArtifact.class, DefaultJvmLibraryJavadocArtifact.class);
                libraries.add(new DefaultJvmLibrary(componentResult.getId(), sourcesArtifacts, javadocArtifacts));
            }
        }
        return libraries;
    }

    private <T extends Artifact, U extends T> Set<T> transform(ResolvedComponentArtifactsResult componentResult, Class<T> type, final Class<U> impl) {
        Set<ArtifactResult> sourceArtifactResults = componentResult.getArtifacts(type);
        final Instantiator instantiator = new DirectInstantiator();
        return CollectionUtils.collect(sourceArtifactResults, new Transformer<T, ArtifactResult>() {
            public T transform(ArtifactResult original) {
                if (original instanceof ResolvedArtifactResult) {
                    return instantiator.newInstance(impl, ((ResolvedArtifactResult) original).getFile());
                } else {
                    return instantiator.newInstance(impl, ((UnresolvedArtifactResult) original).getFailure());
                }
            }
        });
    }
}
