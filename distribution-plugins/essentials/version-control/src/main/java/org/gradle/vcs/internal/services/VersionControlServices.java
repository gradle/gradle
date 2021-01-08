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

package org.gradle.vcs.internal.services;

import org.gradle.StartParameter;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.ResolveContext;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ComponentResolvers;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ResolverProviderFactory;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionComparator;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.LocalComponentRegistry;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.notations.ModuleIdentifierNotationConverter;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.model.ObjectFactory;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.internal.CleanupActionFactory;
import org.gradle.initialization.layout.ProjectCacheDir;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.build.PublicBuildPath;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.scopes.AbstractPluginServiceRegistry;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.internal.typeconversion.NotationParserBuilder;
import org.gradle.vcs.SourceControl;
import org.gradle.vcs.VcsMappings;
import org.gradle.vcs.internal.DefaultSourceControl;
import org.gradle.vcs.internal.DefaultVcsMappingFactory;
import org.gradle.vcs.internal.DefaultVcsMappings;
import org.gradle.vcs.internal.DefaultVcsMappingsStore;
import org.gradle.vcs.internal.VcsDirectoryLayout;
import org.gradle.vcs.internal.VcsMappingFactory;
import org.gradle.vcs.internal.VcsMappingsStore;
import org.gradle.vcs.internal.VcsResolver;
import org.gradle.vcs.internal.VersionControlRepositoryConnectionFactory;
import org.gradle.vcs.internal.VersionControlSpecFactory;
import org.gradle.vcs.internal.resolver.DefaultVcsVersionWorkingDirResolver;
import org.gradle.vcs.internal.resolver.OfflineVcsVersionWorkingDirResolver;
import org.gradle.vcs.internal.resolver.OncePerBuildInvocationVcsVersionWorkingDirResolver;
import org.gradle.vcs.internal.resolver.PersistentVcsMetadataCache;
import org.gradle.vcs.internal.resolver.VcsDependencyResolver;
import org.gradle.vcs.internal.resolver.VcsVersionSelectionCache;
import org.gradle.vcs.internal.resolver.VcsVersionWorkingDirResolver;

import java.util.Collection;

public class VersionControlServices extends AbstractPluginServiceRegistry {
    @Override
    public void registerBuildTreeServices(ServiceRegistration registration) {
        registration.addProvider(new VersionControlBuildTreeServices());
    }

    @Override
    public void registerBuildSessionServices(ServiceRegistration registration) {
        registration.addProvider(new VersionControlBuildSessionServices());
    }

    @Override
    public void registerBuildServices(ServiceRegistration registration) {
        registration.addProvider(new VersionControlBuildServices());
    }

    @Override
    public void registerSettingsServices(ServiceRegistration registration) {
        registration.addProvider(new VersionControlSettingsServices());
    }

    private static class VersionControlBuildTreeServices {
        VcsMappingFactory createVcsMappingFactory(ObjectFactory objectFactory, StartParameter startParameter, NotationParser<String, ModuleIdentifier> notationParser, VersionControlSpecFactory versionControlSpecFactory) {
            return new DefaultVcsMappingFactory(objectFactory, versionControlSpecFactory);
        }

        VersionControlSpecFactory createVersionControlSpecFactory(ObjectFactory objectFactory, NotationParser<String, ModuleIdentifier> notationParser) {
            return new DefaultVersionControlSpecFactory(objectFactory, notationParser);
        }

        VcsMappingsStore createVcsMappingsStore(VcsMappingFactory mappingFactory) {
            return new DefaultVcsMappingsStore(mappingFactory);
        }

        VcsResolver createVcsResolver(VcsMappingsStore mappingsStore) {
            return mappingsStore.asResolver();
        }

        VcsVersionSelectionCache createVersionSelectionCache() {
            return new VcsVersionSelectionCache();
        }
    }

    private static class VersionControlBuildSessionServices {
        NotationParser<String, ModuleIdentifier> createModuleIdParser(ImmutableModuleIdentifierFactory moduleIdentifierFactory) {
            return NotationParserBuilder
                .builder(String.class, ModuleIdentifier.class)
                .typeDisplayName("a module identifier")
                .fromCharSequence(new ModuleIdentifierNotationConverter(moduleIdentifierFactory))
                .toComposite();
        }

        VersionControlRepositoryConnectionFactory createVersionControlSystemFactory(VcsDirectoryLayout directoryLayout, CleanupActionFactory cleanupActionFactory, CacheRepository cacheRepository) {
            return new DefaultVersionControlRepositoryFactory(directoryLayout, cacheRepository, cleanupActionFactory);
        }

        VcsDirectoryLayout createVcsWorkingDirectoryRoot(ProjectCacheDir projectCacheDir) {
            return new VcsDirectoryLayout(projectCacheDir.getDir());
        }

        PersistentVcsMetadataCache createMetadataCache(VcsDirectoryLayout directoryLayout, CacheRepository cacheRepository) {
            return new PersistentVcsMetadataCache(directoryLayout, cacheRepository);
        }
    }

    private static class VersionControlSettingsServices {
        VcsMappings createVcsMappings(ObjectFactory objectFactory, VcsMappingsStore vcsMappingsStore, Gradle gradle, NotationParser<String, ModuleIdentifier> notationParser) {
            return objectFactory.newInstance(DefaultVcsMappings.class, vcsMappingsStore, gradle, notationParser);
        }

        SourceControl createSourceControl(ObjectFactory objectFactory, FileResolver fileResolver, VcsMappings vcsMappings, VersionControlSpecFactory specFactory) {
            return objectFactory.newInstance(DefaultSourceControl.class, fileResolver, vcsMappings, specFactory);
        }
    }

    private static class VersionControlBuildServices {
        VcsDependencyResolver createVcsDependencyResolver(LocalComponentRegistry localComponentRegistry, VcsResolver vcsResolver, VersionControlRepositoryConnectionFactory versionControlSystemFactory, VersionSelectorScheme versionSelectorScheme, VersionComparator versionComparator, BuildStateRegistry buildRegistry, VersionParser versionParser, VcsVersionSelectionCache versionSelectionCache, PersistentVcsMetadataCache persistentCache, StartParameter startParameter, PublicBuildPath publicBuildPath) {
            VcsVersionWorkingDirResolver workingDirResolver;
            if (startParameter.isOffline()) {
                workingDirResolver = new OfflineVcsVersionWorkingDirResolver(persistentCache);
            } else {
                workingDirResolver = new DefaultVcsVersionWorkingDirResolver(versionSelectorScheme, versionComparator, versionParser, versionSelectionCache, persistentCache);
            }
            workingDirResolver = new OncePerBuildInvocationVcsVersionWorkingDirResolver(versionSelectionCache, workingDirResolver);
            return new VcsDependencyResolver(localComponentRegistry, vcsResolver, versionControlSystemFactory, buildRegistry, workingDirResolver, publicBuildPath);
        }

        ResolverProviderFactory createVcsResolverProviderFactory(VcsDependencyResolver vcsDependencyResolver, VcsResolver vcsResolver) {
            return new VcsResolverFactory(vcsDependencyResolver, vcsResolver);
        }
    }

    private static class VcsResolverFactory implements ResolverProviderFactory {
        private final VcsDependencyResolver vcsDependencyResolver;
        private final VcsResolver vcsResolver;

        private VcsResolverFactory(VcsDependencyResolver vcsDependencyResolver, VcsResolver vcsResolver) {
            this.vcsDependencyResolver = vcsDependencyResolver;
            this.vcsResolver = vcsResolver;
        }

        @Override
        public void create(ResolveContext context, Collection<ComponentResolvers> resolvers) {
            if (vcsResolver.hasRules()) {
                resolvers.add(vcsDependencyResolver);
            }
        }
    }
}
