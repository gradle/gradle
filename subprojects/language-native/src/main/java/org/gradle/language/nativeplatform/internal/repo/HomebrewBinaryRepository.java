/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.language.nativeplatform.internal.repo;

import com.google.common.collect.ImmutableSortedMap;
import org.gradle.api.Action;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.artifacts.repositories.RepositoryContentDescriptor;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ConfiguredModuleComponentRepository;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;
import org.gradle.api.internal.artifacts.repositories.descriptor.RepositoryDescriptor;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.model.NamedObjectInstantiator;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.internal.os.OperatingSystem;

import javax.inject.Inject;
import java.io.File;

/**
 * A repository that locates Homebrew pre-built binaries. This implementation is a spike and can only find header files.
 *
 * <p>To resolve a library "group:module:version", it looks for a directory called "baseDir/module/version" and if present assumes the library version is installed and available
 * (the group is ignored).
 */
public class HomebrewBinaryRepository implements ArtifactRepository, ResolutionAwareRepository {
    private static final File HOMEBREW_MACOS_CELLAR_DIRECTORY = new File("/usr/local/Cellar");
    private static final File HOMEBREW_SUDO_LINUX_CELLAR_DIRECTORY = new File("/home/linuxbrew/.linuxbrew/Cellar");
    private static final File HOMEBREW_USER_LINUX_CELLAR_DIRECTORY = new File(System.getProperty("user.home") + "/.linuxbrew/Cellar");
    private final ImmutableAttributesFactory attributesFactory;
    private final NamedObjectInstantiator instantiator;
    private final AttributesSchemaInternal schema;
    private final DirectoryProperty location;

    @Inject
    public HomebrewBinaryRepository(ImmutableAttributesFactory attributesFactory, NamedObjectInstantiator instantiator, AttributesSchemaInternal schema, ProviderFactory providerFactory, ObjectFactory objectFactory) {
        this.attributesFactory = attributesFactory;
        this.instantiator = instantiator;
        this.schema = schema;
        this.location = objectFactory.directoryProperty().fileProvider(providerFactory.provider(() -> {
            if (OperatingSystem.current().isMacOsX()) {
                return HOMEBREW_MACOS_CELLAR_DIRECTORY;
            } else if (HOMEBREW_SUDO_LINUX_CELLAR_DIRECTORY.exists()) {
                return HOMEBREW_SUDO_LINUX_CELLAR_DIRECTORY;
            } else if (HOMEBREW_USER_LINUX_CELLAR_DIRECTORY.exists()) {
                return HOMEBREW_USER_LINUX_CELLAR_DIRECTORY;
            }
            return null;
        }));
    }

    @Override
    public String getName() {
        return "homebrew";
    }

    @Override
    public void setName(String name) {
        throw new UnsupportedOperationException();
    }

    public DirectoryProperty getLocation() {
        return location;
    }

    @Override
    public void content(Action<? super RepositoryContentDescriptor> configureAction) {
        // Ignore for now
        throw new UnsupportedOperationException();
    }

    @Override
    public RepositoryDescriptor getDescriptor() {
        return new HomebrewRepositoryDescriptor();
    }

    @Override
    public ConfiguredModuleComponentRepository createResolver() {
        return new HomebrewModuleComponentRepository(getName(), getLocation().get().getAsFile(), attributesFactory, instantiator, schema);
    }

    private class HomebrewRepositoryDescriptor extends RepositoryDescriptor {
        public HomebrewRepositoryDescriptor() {
            super(HomebrewBinaryRepository.this.getName());
        }

        @Override
        public Type getType() {
            throw new UnsupportedOperationException();
        }

        @Override
        protected void addProperties(ImmutableSortedMap.Builder<String, Object> builder) {
            // Ignore for now
        }
    }
}
