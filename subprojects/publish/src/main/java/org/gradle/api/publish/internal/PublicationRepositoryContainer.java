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

package org.gradle.api.publish.internal;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Namer;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.internal.AbstractNamedDomainObjectContainer;
import org.gradle.api.internal.artifacts.repositories.ArtifactRepositoryInternal;
import org.gradle.internal.reflect.Instantiator;

public class PublicationRepositoryContainer extends AbstractNamedDomainObjectContainer<ArtifactRepository> {

    private Transformer<? extends ArtifactRepository, String> factory;

    public PublicationRepositoryContainer(Instantiator instantiator) {
        super(ArtifactRepositoryInternal.class, instantiator, new Namer<ArtifactRepository>() {
            public String determineName(ArtifactRepository object) {
                return object.getName();
            }
        });
    }

    public Transformer<? extends ArtifactRepository, String> getFactory() {
        return factory;
    }

    public void setFactory(Transformer<? extends ArtifactRepository, String> factory) {
        this.factory = factory;
    }

    @Override
    protected ArtifactRepository doCreate(String name) {
        if (factory == null) {
            throw new InvalidUserDataException("No factory has been set for this publication repository container. Please apply the 'ivy-publish' plugin to install the IvyArtifactRepository factory");
        }
        return factory.transform(name);
    }
}
