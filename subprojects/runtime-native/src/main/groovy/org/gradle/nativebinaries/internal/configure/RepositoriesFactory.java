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

import com.google.common.collect.ImmutableList;
import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectFactory;
import org.gradle.api.Namer;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.internal.DefaultPolymorphicDomainObjectContainer;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.MethodModelRuleDescriptor;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.nativebinaries.*;
import org.gradle.nativebinaries.internal.prebuilt.DefaultPrebuiltLibraries;
import org.gradle.nativebinaries.internal.prebuilt.PrebuiltLibraryInitializer;
import org.gradle.nativebinaries.platform.PlatformContainer;

import java.lang.reflect.Method;
import java.util.List;

public class RepositoriesFactory implements ModelCreator {
    private final ModelPath path;
    private final ModelType<Repositories> type = ModelType.of(Repositories.class);
    private final ModelPromise promise = new SingleTypeModelPromise(type);
    private final Instantiator instantiator;
    private final FileResolver fileResolver;

    public RepositoriesFactory(String modelPath, Instantiator instantiator, FileResolver fileResolver) {
        this.path = new ModelPath(modelPath);
        this.instantiator = instantiator;
        this.fileResolver = fileResolver;
    }

    public List<ModelReference<?>> getInputs() {
        return ImmutableList.<ModelReference<?>>of(
                ModelReference.of("flavors", FlavorContainer.class),
                ModelReference.of("platforms", PlatformContainer.class),
                ModelReference.of("buildTypes", BuildTypeContainer.class)
        );
    }

    private final ModelRuleDescriptor descriptor = new MethodModelRuleDescriptor(findCreateMethod());

    private static Method findCreateMethod() {
        try {
            return RepositoriesFactory.class.getDeclaredMethod("create", Inputs.class);
        } catch (NoSuchMethodException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    public ModelRuleDescriptor getDescriptor() {
        return descriptor;
    }

    public ModelAdapter create(Inputs inputs) {
        FlavorContainer flavors = inputs.get(0, ModelType.of(FlavorContainer.class)).getInstance();
        PlatformContainer platforms = inputs.get(1, ModelType.of(PlatformContainer.class)).getInstance();
        BuildTypeContainer buildTypes = inputs.get(2, ModelType.of(BuildTypeContainer.class)).getInstance();
        Action<PrebuiltLibrary> initializer = new PrebuiltLibraryInitializer(instantiator, platforms, buildTypes, flavors);
        return InstanceModelAdapter.of(type, new DefaultRepositories(instantiator, fileResolver, initializer));
    }

    public ModelPath getPath() {
        return path;
    }

    public ModelPromise getPromise() {
        return promise;
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
