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
package org.gradle.nativebinaries.internal.configure;

import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectFactory;
import org.gradle.api.Namer;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.internal.DefaultPolymorphicDomainObjectContainer;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.model.internal.Inputs;
import org.gradle.model.internal.ModelCreator;
import org.gradle.nativebinaries.*;
import org.gradle.nativebinaries.internal.prebuilt.DefaultPrebuiltLibraries;
import org.gradle.nativebinaries.internal.prebuilt.PrebuiltLibraryInitializer;
import org.gradle.nativebinaries.platform.PlatformContainer;

public class RepositoriesFactory implements ModelCreator<Repositories> {
    private final Instantiator instantiator;
    private final FileResolver fileResolver;

    public RepositoriesFactory(Instantiator instantiator, FileResolver fileResolver) {
        this.instantiator = instantiator;
        this.fileResolver = fileResolver;
    }

    public Repositories create(Inputs inputs) {
        FlavorContainer flavors = inputs.get(0, FlavorContainer.class);
        PlatformContainer platforms = inputs.get(1, PlatformContainer.class);
        BuildTypeContainer buildTypes = inputs.get(2, BuildTypeContainer.class);
        Action<PrebuiltLibrary> initializer = new PrebuiltLibraryInitializer(instantiator, platforms, buildTypes, flavors);
        return new DefaultRepositories(instantiator, fileResolver, initializer);
    }

    public Class<Repositories> getType() {
        return Repositories.class;
    }

    private static class DefaultRepositories extends DefaultPolymorphicDomainObjectContainer<ArtifactRepository> implements Repositories {
        private DefaultRepositories(final Instantiator instantiator, final FileResolver fileResolver, final Action<PrebuiltLibrary> binaryFactory) {
            super(ArtifactRepository.class, instantiator, new ArtifactRepositoryNamer());
            registerFactory(PrebuiltLibraries.class, new NamedDomainObjectFactory<PrebuiltLibraries>() {
                public PrebuiltLibraries create(String name) {
                    return instantiator.newInstance(DefaultPrebuiltLibraries.class, name, instantiator, fileResolver, binaryFactory);
                }
            });
        }
    }

    private static class ArtifactRepositoryNamer implements Namer<ArtifactRepository> {
        public String determineName(ArtifactRepository object) {
            return object.getName();
        }
    }
}
