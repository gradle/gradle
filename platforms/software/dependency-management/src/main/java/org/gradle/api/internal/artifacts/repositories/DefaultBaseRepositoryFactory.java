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

package org.gradle.api.internal.artifacts.repositories;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Iterables;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.ComponentMetadataSupplierDetails;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.artifacts.repositories.AuthenticationContainer;
import org.gradle.api.artifacts.repositories.FlatDirectoryArtifactRepository;
import org.gradle.api.artifacts.repositories.IvyArtifactRepository;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.artifacts.BaseRepositoryFactory;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.NamedVariantIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.IvyContextManager;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ComponentResolvers;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ConfiguredModuleComponentRepository;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleComponentRepositoryAccess;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.GradleModuleMetadataParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MetaDataParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;
import org.gradle.api.internal.artifacts.mvnsettings.LocalMavenRepositoryLocator;
import org.gradle.api.internal.artifacts.repositories.descriptor.RepositoryDescriptor;
import org.gradle.api.internal.artifacts.repositories.metadata.IvyMutableModuleMetadataFactory;
import org.gradle.api.internal.artifacts.repositories.metadata.MavenMutableModuleMetadataFactory;
import org.gradle.api.internal.artifacts.repositories.resolver.MetadataFetchingCost;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory;
import org.gradle.api.internal.attributes.AttributesFactory;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema;
import org.gradle.api.internal.classpath.Module;
import org.gradle.api.internal.classpath.ModuleRegistry;
import org.gradle.api.internal.component.ArtifactType;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.authentication.Authentication;
import org.gradle.internal.action.InstantiatingAction;
import org.gradle.internal.authentication.AuthenticationSchemeRegistry;
import org.gradle.internal.authentication.DefaultAuthenticationContainer;
import org.gradle.internal.component.external.model.ComponentVariant;
import org.gradle.internal.component.external.model.DefaultConfigurationMetadata;
import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactMetadata;
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier;
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector;
import org.gradle.internal.component.external.model.ExternalModuleVariantGraphResolveMetadata;
import org.gradle.internal.component.external.model.GradleDependencyMetadata;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata;
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata;
import org.gradle.internal.component.external.model.ModuleDependencyMetadata;
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata;
import org.gradle.internal.component.external.model.NoOpDerivationStrategy;
import org.gradle.internal.component.external.model.VariantDerivationStrategy;
import org.gradle.internal.component.external.model.VariantMetadataRules;
import org.gradle.internal.component.external.model.VirtualComponentIdentifier;
import org.gradle.internal.component.external.model.maven.MutableMavenModuleResolveMetadata;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentArtifactResolveMetadata;
import org.gradle.internal.component.model.ComponentOverrideMetadata;
import org.gradle.internal.component.model.ImmutableModuleSources;
import org.gradle.internal.component.model.ModuleConfigurationMetadata;
import org.gradle.internal.component.model.ModuleSources;
import org.gradle.internal.hash.ChecksumService;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.isolation.IsolatableFactory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.resolve.result.BuildableArtifactFileResolveResult;
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult;
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult;
import org.gradle.internal.resolve.result.BuildableModuleVersionListingResolveResult;
import org.gradle.internal.resource.local.FileResourceRepository;
import org.gradle.internal.resource.local.FileStore;
import org.gradle.internal.resource.local.LocallyAvailableResourceFinder;
import org.gradle.util.GradleVersion;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DefaultBaseRepositoryFactory implements BaseRepositoryFactory {
    private final LocalMavenRepositoryLocator localMavenRepositoryLocator;
    private final FileResolver fileResolver;
    private final Instantiator instantiator;
    private final RepositoryTransportFactory transportFactory;
    private final LocallyAvailableResourceFinder<ModuleComponentArtifactMetadata> locallyAvailableResourceFinder;
    private final FileStore<ModuleComponentArtifactIdentifier> artifactFileStore;
    private final FileStore<String> externalResourcesFileStore;
    private final MetaDataParser<MutableMavenModuleResolveMetadata> pomParser;
    private final FileCollectionFactory fileCollectionFactory;
    private final GradleModuleMetadataParser metadataParser;
    private final AuthenticationSchemeRegistry authenticationSchemeRegistry;
    private final IvyContextManager ivyContextManager;
    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;
    private final InstantiatorFactory instantiatorFactory;
    private final FileResourceRepository fileResourceRepository;
    private final MavenMutableModuleMetadataFactory mavenMetadataFactory;
    private final IvyMutableModuleMetadataFactory ivyMetadataFactory;
    private final IsolatableFactory isolatableFactory;
    private final ObjectFactory objectFactory;
    private final CollectionCallbackActionDecorator callbackActionDecorator;
    private final DefaultUrlArtifactRepository.Factory urlArtifactRepositoryFactory;
    private final ChecksumService checksumService;
    private final ProviderFactory providerFactory;
    private final VersionParser versionParser;
    private final ModuleRegistry moduleRegistry;

    public DefaultBaseRepositoryFactory(LocalMavenRepositoryLocator localMavenRepositoryLocator,
                                        FileResolver fileResolver,
                                        FileCollectionFactory fileCollectionFactory,
                                        RepositoryTransportFactory transportFactory,
                                        LocallyAvailableResourceFinder<ModuleComponentArtifactMetadata> locallyAvailableResourceFinder,
                                        FileStore<ModuleComponentArtifactIdentifier> artifactFileStore,
                                        FileStore<String> externalResourcesFileStore,
                                        MetaDataParser<MutableMavenModuleResolveMetadata> pomParser,
                                        GradleModuleMetadataParser metadataParser,
                                        AuthenticationSchemeRegistry authenticationSchemeRegistry,
                                        IvyContextManager ivyContextManager,
                                        ImmutableModuleIdentifierFactory moduleIdentifierFactory,
                                        InstantiatorFactory instantiatorFactory,
                                        FileResourceRepository fileResourceRepository,
                                        MavenMutableModuleMetadataFactory mavenMetadataFactory,
                                        IvyMutableModuleMetadataFactory ivyMetadataFactory,
                                        IsolatableFactory isolatableFactory,
                                        ObjectFactory objectFactory,
                                        CollectionCallbackActionDecorator callbackActionDecorator,
                                        DefaultUrlArtifactRepository.Factory urlArtifactRepositoryFactory,
                                        ChecksumService checksumService,
                                        ProviderFactory providerFactory,
                                        VersionParser versionParser,
                                        ModuleRegistry moduleRegistry
    ) {
        this.localMavenRepositoryLocator = localMavenRepositoryLocator;
        this.fileResolver = fileResolver;
        this.fileCollectionFactory = fileCollectionFactory;
        this.metadataParser = metadataParser;
        this.instantiator = instantiatorFactory.decorateLenient();
        this.transportFactory = transportFactory;
        this.locallyAvailableResourceFinder = locallyAvailableResourceFinder;
        this.artifactFileStore = artifactFileStore;
        this.externalResourcesFileStore = externalResourcesFileStore;
        this.pomParser = pomParser;
        this.authenticationSchemeRegistry = authenticationSchemeRegistry;
        this.ivyContextManager = ivyContextManager;
        this.moduleIdentifierFactory = moduleIdentifierFactory;
        this.instantiatorFactory = instantiatorFactory;
        this.fileResourceRepository = fileResourceRepository;
        this.mavenMetadataFactory = mavenMetadataFactory;
        this.ivyMetadataFactory = ivyMetadataFactory;
        this.isolatableFactory = isolatableFactory;
        this.objectFactory = objectFactory;
        this.callbackActionDecorator = callbackActionDecorator;
        this.urlArtifactRepositoryFactory = urlArtifactRepositoryFactory;
        this.checksumService = checksumService;
        this.providerFactory = providerFactory;
        this.versionParser = versionParser;
        this.moduleRegistry = moduleRegistry;
    }

    @Override
    public FlatDirectoryArtifactRepository createFlatDirRepository() {
        return objectFactory.newInstance(DefaultFlatDirArtifactRepository.class, fileCollectionFactory, transportFactory, locallyAvailableResourceFinder, artifactFileStore, ivyMetadataFactory, instantiatorFactory, objectFactory, checksumService, versionParser);
    }

    @Override
    public ArtifactRepository createGradleDistributionRepository() {
        return new GradleDistributionRepository(
            objectFactory,
            versionParser,
            instantiatorFactory,
            moduleRegistry,
            moduleIdentifierFactory
        );
    }

    private static class GradleDistributionRepository extends AbstractResolutionAwareArtifactRepository<RepositoryDescriptor> {

        private final InstantiatorFactory instantiatorFactory;
        private final ModuleRegistry moduleRegistry;
        private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;

        public GradleDistributionRepository(
            ObjectFactory objectFactory,
            VersionParser versionParser,
            InstantiatorFactory instantiatorFactory,
            ModuleRegistry moduleRegistry,
            ImmutableModuleIdentifierFactory moduleIdentifierFactory
        ) {
            super(objectFactory, versionParser);
            this.instantiatorFactory = instantiatorFactory;
            this.moduleRegistry = moduleRegistry;
            this.moduleIdentifierFactory = moduleIdentifierFactory;
        }

        @Override
        protected RepositoryDescriptor createDescriptor() {
            String id = GradleVersion.current().getVersion();
            return new RepositoryDescriptor(getName(), id) {
                @Override
                public Type getType() {
                    return Type.DISTRIBUTION;
                }

                @Override
                protected void addProperties(ImmutableSortedMap.Builder<String, Object> builder) {

                }
            };
        }

        @Override
        public ConfiguredModuleComponentRepository createResolver() {
            return new DistributionModuleComponentRepository(
                getDescriptor(),
                instantiatorFactory.inject(),
                moduleRegistry,
                moduleIdentifierFactory
            );
        }

    }

    private static class DistributionModuleComponentRepository implements ConfiguredModuleComponentRepository {

        private final RepositoryDescriptor descriptor;
        private final Instantiator instantiator;
        private final ModuleComponentRepositoryAccess<ModuleComponentResolveMetadata> repositoryAccess;

        public DistributionModuleComponentRepository(RepositoryDescriptor descriptor, Instantiator instantiator, ModuleRegistry moduleRegistry, ImmutableModuleIdentifierFactory moduleIdentifierFactory1) {
            this.descriptor = descriptor;
            this.instantiator = instantiator;
            this.repositoryAccess = new DistributionRepositoryAccess(moduleRegistry, moduleIdentifierFactory1);
        }

        @Override
        public boolean isDynamicResolveMode() {
            return false;
        }

        @Override
        public boolean isLocal() {
            return true;
        }

        @Override
        public void setComponentResolvers(ComponentResolvers resolver) {
            // Ignore, we don't need to resolve any additional components.
        }

        @Override
        public Instantiator getComponentMetadataInstantiator() {
            return instantiator;
        }

        @Override
        public String getId() {
            return descriptor.getId();
        }

        @Override
        public String getName() {
            return descriptor.getName();
        }

        @Override
        public ModuleComponentRepositoryAccess<ModuleComponentResolveMetadata> getLocalAccess() {
            return repositoryAccess;
        }

        @Override
        public ModuleComponentRepositoryAccess<ModuleComponentResolveMetadata> getRemoteAccess() {
            return repositoryAccess;
        }

        @Override
        public Map<ComponentArtifactIdentifier, ResolvableArtifact> getArtifactCache() {
            throw new UnsupportedOperationException();
        }

        @Override
        public @Nullable InstantiatingAction<ComponentMetadataSupplierDetails> getComponentMetadataSupplier() {
            return null;
        }

        @Override
        public boolean isContinueOnConnectionFailure() {
            return false;
        }

        @Override
        public boolean isRepositoryDisabled() {
            return false;
        }

    }

    private static class DistributionRepositoryAccess implements ModuleComponentRepositoryAccess<ModuleComponentResolveMetadata> {

        private static final ImmutableSet<String> TOP_LEVEL_MODULE_NAMES = ImmutableSet.of("public-api");

        private final ModuleRegistry moduleRegistry;
        private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;

        private final ImmutableMap<ModuleComponentIdentifier, Module> idToModule;

        public DistributionRepositoryAccess(ModuleRegistry moduleRegistry, ImmutableModuleIdentifierFactory moduleIdentifierFactory) {
            this.moduleRegistry = moduleRegistry;
            this.moduleIdentifierFactory = moduleIdentifierFactory;

            // TODO: Do this once. We don't need to do this for each instance of the distribution repository.
            this.idToModule = collectAvailableModules(moduleRegistry, TOP_LEVEL_MODULE_NAMES);
        }

        private static ImmutableMap<ModuleComponentIdentifier, Module> collectAvailableModules(ModuleRegistry moduleRegistry, ImmutableSet<String> topLevelModuleNames) {
            Set<Module> allAccessibleModules = moduleRegistry.getRuntimeModules(Iterables.transform(topLevelModuleNames, moduleRegistry::getModule));
            ImmutableMap.Builder<ModuleComponentIdentifier, Module> builder = ImmutableMap.builderWithExpectedSize(allAccessibleModules.size());
            for (Module module : allAccessibleModules) {
                Module.ModuleAlias info = module.getAlias();
                if (info == null) {
                    throw new UnsupportedOperationException("Module " + module.getName() + " has no alias");
                }
                builder.put(DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId(info.getGroup(), info.getName()), info.getVersion()), module);
            }
            return builder.build();
        }

        @Override
        public void listModuleVersions(ModuleComponentSelector selector, ComponentOverrideMetadata overrideMetadata, BuildableModuleVersionListingResolveResult result) {
            // Requesting a dynamic version from the distribution repository is currently unsupported.
        }

        @Override
        public void resolveComponentMetaData(ModuleComponentIdentifier moduleComponentIdentifier, ComponentOverrideMetadata requestMetaData, BuildableModuleComponentMetaDataResolveResult<ModuleComponentResolveMetadata> result) {
            Module module = idToModule.get(moduleComponentIdentifier);
            if (module == null) {
                // The distribution does not provide this dependency, or we do not expose it.
                return;
            }

            if (requestMetaData.getArtifact() != null) {
                throw new UnsupportedOperationException("Cannot request explicit artifact from distribution repository");
            }

            ModuleVersionIdentifier moduleVersionId = moduleIdentifierFactory.moduleWithVersion(moduleComponentIdentifier.getModuleIdentifier(), moduleComponentIdentifier.getVersion());
            result.resolved(new DistributionModuleComponentResolveMetadata(moduleComponentIdentifier, moduleVersionId, module, moduleRegistry, ImmutableModuleSources.of()));
        }

        @Override
        public void resolveArtifactsWithType(ComponentArtifactResolveMetadata component, ArtifactType artifactType, BuildableArtifactSetResolveResult result) {
            // This is a legacy resolution mechanism.
            // Distribution repositories should never support this.
        }

        @Override
        public void resolveArtifact(ComponentArtifactMetadata artifact, ModuleSources moduleSources, BuildableArtifactFileResolveResult result) {
            if (!(artifact.getId() instanceof DistributionFileArtifactIdentifier)) {
                return;
            }
            result.resolved(((DistributionFileArtifactIdentifier) artifact.getId()).getFile());
        }

        @Override
        public MetadataFetchingCost estimateMetadataFetchingCost(ModuleComponentIdentifier moduleComponentIdentifier) {
            if (idToModule.containsKey(moduleComponentIdentifier)) {
                return MetadataFetchingCost.FAST;
            } else {
                return MetadataFetchingCost.CHEAP;
            }
        }
    }

    private static class DistributionModuleComponentResolveMetadata implements ModuleComponentResolveMetadata {

        private final ModuleComponentIdentifier id;
        private final ModuleVersionIdentifier moduleVersionId;
        private final Module distributionModule;
        private final ModuleRegistry moduleRegistry;
        private final ModuleSources sources;

        public DistributionModuleComponentResolveMetadata(
            ModuleComponentIdentifier id,
            ModuleVersionIdentifier moduleVersionId,
            Module distributionModule,
            ModuleRegistry moduleRegistry,
            ModuleSources sources
        ) {
            this.id = id;
            this.moduleVersionId = moduleVersionId;
            this.distributionModule = distributionModule;
            this.moduleRegistry = moduleRegistry;
            this.sources = sources;
        }

        @Override
        public ModuleComponentIdentifier getId() {
            return id;
        }

        @Override
        public MutableModuleComponentResolveMetadata asMutable() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ModuleComponentResolveMetadata withSources(ModuleSources sources) {
            return new DistributionModuleComponentResolveMetadata(id, moduleVersionId, distributionModule, moduleRegistry, sources);
        }

        @Override
        public ModuleComponentResolveMetadata withDerivationStrategy(VariantDerivationStrategy derivationStrategy) {
            throw new UnsupportedOperationException();
        }

        @Override
        public @Nullable ModuleConfigurationMetadata getConfiguration(String name) {
            if (!name.equals(Dependency.DEFAULT_CONFIGURATION)) {
                return null;
            }

            List<File> classpath = distributionModule.getImplementationClasspath().getAsFiles();
            ImmutableList.Builder<ModuleComponentArtifactMetadata> artifacts = ImmutableList.builderWithExpectedSize(classpath.size());
            for (File file : classpath) {
                artifacts.add(new DefaultModuleComponentArtifactMetadata(new DistributionFileArtifactIdentifier(getId(), distributionModule.getName(), file)));
            }

            Collection<String> dependencyModules = distributionModule.getDependencyNames();
            ImmutableList.Builder<ModuleDependencyMetadata> dependencies = ImmutableList.builderWithExpectedSize(dependencyModules.size());
            for (String dependencyName : dependencyModules) {
                Module dependencyModule = moduleRegistry.getModule(dependencyName);
                Module.ModuleAlias alias = dependencyModule.getAlias();
                if (alias == null) {
                    throw new IllegalStateException("Dependency module " + dependencyModule + " has no alias");
                }
                dependencies.add(new GradleDependencyMetadata(
                    DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId(alias.getGroup(), alias.getName()), alias.getVersion()),
                    ImmutableList.of(),
                    false,
                    false,
                    null,
                    false,
                    null
                ));
            }

            DefaultConfigurationMetadata configuration = new DefaultConfigurationMetadata(
                Dependency.DEFAULT_CONFIGURATION,
                new NamedVariantIdentifier(getId(), Dependency.DEFAULT_CONFIGURATION),
                getId(),
                true,
                false,
                ImmutableSet.of(),
                artifacts.build(),
                getVariantMetadataRules(),
                ImmutableList.of(),
                ImmutableAttributes.EMPTY,
                false
            );
            configuration.setDependencies(dependencies.build());
            return configuration;
        }

        @Override
        public ImmutableList<? extends ComponentVariant> getVariants() {
            return ImmutableList.of();
        }

        @Override
        public @Nullable AttributesFactory getAttributesFactory() {
            return null;
        }

        @Override
        public VariantMetadataRules getVariantMetadataRules() {
            return VariantMetadataRules.noOp();
        }

        @Override
        public VariantDerivationStrategy getVariantDerivationStrategy() {
            return NoOpDerivationStrategy.getInstance();
        }

        @Override
        public boolean isExternalVariant() {
            return false;
        }

        @Override
        public boolean isComponentMetadataRuleCachingEnabled() {
            return false;
        }

        @Override
        public ModuleVersionIdentifier getModuleVersionId() {
            return moduleVersionId;
        }

        @Override
        public ModuleSources getSources() {
            return sources;
        }

        @Override
        public ImmutableAttributesSchema getAttributesSchema() {
            return ImmutableAttributesSchema.EMPTY;
        }

        @Override
        public boolean isMissing() {
            return false;
        }

        @Override
        public boolean isChanging() {
            return false;
        }

        @Override
        public @Nullable String getStatus() {
            return null;
        }

        @Override
        public @Nullable List<String> getStatusScheme() {
            return null;
        }

        @Override
        public ImmutableList<? extends VirtualComponentIdentifier> getPlatformOwners() {
            return ImmutableList.of();
        }

        @Override
        public ImmutableAttributes getAttributes() {
            return ImmutableAttributes.EMPTY;
        }

        @Override
        public List<? extends ExternalModuleVariantGraphResolveMetadata> getVariantsForGraphTraversal() {
            return ImmutableList.of();
        }

        @Override
        public Set<String> getConfigurationNames() {
            return ImmutableSet.of(Dependency.DEFAULT_CONFIGURATION);
        }
    }

    private static class DistributionFileArtifactIdentifier implements ModuleComponentArtifactIdentifier {

        private final ModuleComponentIdentifier componentId;
        private final String moduleName;
        private final File file;

        public DistributionFileArtifactIdentifier(ModuleComponentIdentifier componentId, String moduleName, File file) {
            this.componentId = componentId;
            this.moduleName = moduleName;
            this.file = file;
        }

        @Override
        public ModuleComponentIdentifier getComponentIdentifier() {
            return componentId;
        }

        public File getFile() {
            return file;
        }

        @Override
        public String getFileName() {
            return file.getName();
        }

        @Override
        public String getDisplayName() {
            return moduleName + " " + file.getName();
        }

        @Override
        public String getCapitalizedDisplayName() {
            return getDisplayName();
        }
    }

    @Override
    public ArtifactRepository createGradlePluginPortal() {
        MavenArtifactRepository mavenRepository = createMavenRepository(new NamedMavenRepositoryDescriber(PLUGIN_PORTAL_DEFAULT_URL));
        mavenRepository.setUrl(System.getProperty(PLUGIN_PORTAL_OVERRIDE_URL_PROPERTY, PLUGIN_PORTAL_DEFAULT_URL));
        mavenRepository.metadataSources(MavenArtifactRepository.MetadataSources::mavenPom);
        return mavenRepository;
    }

    @Override
    public MavenArtifactRepository createMavenLocalRepository() {
        MavenArtifactRepository mavenRepository = objectFactory.newInstance(DefaultMavenLocalArtifactRepository.class, fileResolver, transportFactory, locallyAvailableResourceFinder, instantiatorFactory, artifactFileStore, pomParser, metadataParser, createAuthenticationContainer(), fileResourceRepository, mavenMetadataFactory, isolatableFactory, objectFactory, urlArtifactRepositoryFactory, checksumService, versionParser);
        File localMavenRepository = localMavenRepositoryLocator.getLocalMavenRepository();
        mavenRepository.setUrl(localMavenRepository);
        return mavenRepository;
    }

    @Override
    public MavenArtifactRepository createMavenCentralRepository() {
        MavenArtifactRepository mavenRepository = createMavenRepository(new NamedMavenRepositoryDescriber(RepositoryHandler.MAVEN_CENTRAL_URL));
        mavenRepository.setUrl(RepositoryHandler.MAVEN_CENTRAL_URL);
        return mavenRepository;
    }

    @Override
    public MavenArtifactRepository createGoogleRepository() {
        MavenArtifactRepository mavenRepository = createMavenRepository(new NamedMavenRepositoryDescriber(RepositoryHandler.GOOGLE_URL));
        mavenRepository.setUrl(RepositoryHandler.GOOGLE_URL);
        return mavenRepository;
    }

    @Override
    public IvyArtifactRepository createIvyRepository() {
        IvyArtifactRepository repository = objectFactory.newInstance(DefaultIvyArtifactRepository.class, fileResolver, transportFactory, locallyAvailableResourceFinder, artifactFileStore, externalResourcesFileStore, createAuthenticationContainer(), ivyContextManager, moduleIdentifierFactory, instantiatorFactory, fileResourceRepository, metadataParser, ivyMetadataFactory, isolatableFactory, objectFactory, urlArtifactRepositoryFactory, checksumService, providerFactory, versionParser);
        repository.getAllowInsecureContinueWhenDisabled().convention(false);
        return repository;
    }

    @Override
    public MavenArtifactRepository createMavenRepository() {
        return createMavenRepository(new DefaultMavenArtifactRepository.DefaultDescriber());
    }

    private MavenArtifactRepository createMavenRepository(Transformer<String, MavenArtifactRepository> describer) {
        MavenArtifactRepository repository = objectFactory.newInstance(DefaultMavenArtifactRepository.class, describer, fileResolver, transportFactory, locallyAvailableResourceFinder, instantiatorFactory, artifactFileStore, pomParser, metadataParser, createAuthenticationContainer(), externalResourcesFileStore, fileResourceRepository, mavenMetadataFactory, isolatableFactory, objectFactory, urlArtifactRepositoryFactory, checksumService, providerFactory, versionParser);
        repository.getAllowInsecureContinueWhenDisabled().convention(false);
        return repository;
    }

    protected AuthenticationContainer createAuthenticationContainer() {
        DefaultAuthenticationContainer container = objectFactory.newInstance(DefaultAuthenticationContainer.class, instantiator, callbackActionDecorator);

        for (Map.Entry<Class<Authentication>, Class<? extends Authentication>> e : authenticationSchemeRegistry.getRegisteredSchemes().entrySet()) {
            container.registerBinding(e.getKey(), e.getValue());
        }

        return container;
    }

    private static class NamedMavenRepositoryDescriber implements Transformer<String, MavenArtifactRepository> {
        private final String defaultUrl;

        private NamedMavenRepositoryDescriber(String defaultUrl) {
            this.defaultUrl = defaultUrl;
        }

        @Override
        public String transform(MavenArtifactRepository repository) {
            URI url = repository.getUrl();
            if (url == null || defaultUrl.equals(url.toString())) {
                return repository.getName();
            }
            return repository.getName() + '(' + url + ')';
        }
    }
}
