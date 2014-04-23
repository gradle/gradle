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

package org.gradle.language.jvm.internal;

import org.gradle.api.Transformer;
import org.gradle.api.artifacts.result.*;
import org.gradle.language.jvm.JvmLibrary;
import org.gradle.api.artifacts.result.jvm.JvmLibraryJavadocArtifact;
import org.gradle.api.artifacts.result.jvm.JvmLibrarySourcesArtifact;
import org.gradle.api.internal.artifacts.dsl.dependencies.ComponentTransformer;
import org.gradle.internal.reflect.DirectInstantiator;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.util.CollectionUtils;

import java.util.Set;

public class JvmLibraryComponentFactory implements ComponentTransformer<JvmLibrary> {
    public Class<JvmLibrary> getType() {
        return JvmLibrary.class;
    }

    public JvmLibrary transform(ResolvedComponentArtifactsResult componentResult) {
        Set<JvmLibrarySourcesArtifact> sourcesArtifacts = transform(componentResult, JvmLibrarySourcesArtifact.class, DefaultJvmLibrarySourcesArtifact.class);
        Set<JvmLibraryJavadocArtifact> javadocArtifacts = transform(componentResult, JvmLibraryJavadocArtifact.class, DefaultJvmLibraryJavadocArtifact.class);
        return new DefaultJvmLibrary(componentResult.getId(), sourcesArtifacts, javadocArtifacts);
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
