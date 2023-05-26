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

package org.gradle.language.cpp.internal;

import org.gradle.api.Action;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.artifacts.configurations.ConfigurationRolesForMigration;
import org.gradle.api.internal.artifacts.configurations.RoleBasedConfigurationContainerInternal;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.language.LibraryDependencies;
import org.gradle.language.cpp.CppBinary;
import org.gradle.language.cpp.CppLibrary;
import org.gradle.language.cpp.CppPlatform;
import org.gradle.language.internal.DefaultLibraryDependencies;
import org.gradle.language.nativeplatform.internal.PublicationAwareComponent;
import org.gradle.nativeplatform.Linkage;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;

import javax.inject.Inject;
import java.util.Collections;

public class DefaultCppLibrary extends DefaultCppComponent implements CppLibrary, PublicationAwareComponent {
    private final ObjectFactory objectFactory;
    private final ConfigurableFileCollection publicHeaders;
    private final FileCollection publicHeadersWithConvention;
    private final SetProperty<Linkage> linkage;
    private final Property<CppBinary> developmentBinary;
    private final Configuration apiElements;
    private final MainLibraryVariant mainVariant;
    private final DefaultLibraryDependencies dependencies;

    @Inject
    public DefaultCppLibrary(String name, ObjectFactory objectFactory, RoleBasedConfigurationContainerInternal configurations, ImmutableAttributesFactory immutableAttributesFactory) {
        super(name, objectFactory);
        this.objectFactory = objectFactory;
        this.developmentBinary = objectFactory.property(CppBinary.class);
        publicHeaders = objectFactory.fileCollection();
        publicHeadersWithConvention = createDirView(publicHeaders, "src/" + name + "/public");

        linkage = objectFactory.setProperty(Linkage.class);
        linkage.set(Collections.singleton(Linkage.SHARED));

        dependencies = objectFactory.newInstance(DefaultLibraryDependencies.class, getNames().withSuffix("implementation"), getNames().withSuffix("api"));

        Usage apiUsage = objectFactory.named(Usage.class, Usage.C_PLUS_PLUS_API);

        apiElements = configurations.migratingUnlocked(getNames().withSuffix("cppApiElements"), ConfigurationRolesForMigration.CONSUMABLE_BUCKET_TO_CONSUMABLE).get();
        apiElements.extendsFrom(dependencies.getApiDependencies());
        apiElements.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, apiUsage);
        apiElements.getAttributes().attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.DIRECTORY_TYPE);

        AttributeContainer publicationAttributes = immutableAttributesFactory.mutable();
        publicationAttributes.attribute(Usage.USAGE_ATTRIBUTE, apiUsage);
        publicationAttributes.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.ZIP_TYPE);
        mainVariant = new MainLibraryVariant("api", apiElements, publicationAttributes, objectFactory);
    }

    public DefaultCppSharedLibrary addSharedLibrary(NativeVariantIdentity identity, CppPlatform targetPlatform, NativeToolChainInternal toolChain, PlatformToolProvider platformToolProvider) {
        DefaultCppSharedLibrary result = objectFactory.newInstance(DefaultCppSharedLibrary.class, getNames().append(identity.getName()), getBaseName(), getCppSource(), getAllHeaderDirs(), getImplementationDependencies(), targetPlatform, toolChain, platformToolProvider, identity);
        getBinaries().add(result);
        return result;
    }

    public DefaultCppStaticLibrary addStaticLibrary(NativeVariantIdentity identity, CppPlatform targetPlatform, NativeToolChainInternal toolChain, PlatformToolProvider platformToolProvider) {
        DefaultCppStaticLibrary result = objectFactory.newInstance(DefaultCppStaticLibrary.class, getNames().append(identity.getName()), getBaseName(), getCppSource(), getAllHeaderDirs(), getImplementationDependencies(), targetPlatform, toolChain, platformToolProvider, identity);
        getBinaries().add(result);
        return result;
    }

    @Override
    public DisplayName getDisplayName() {
        return Describables.withTypeAndName("C++ library", getName());
    }

    @Override
    public Configuration getImplementationDependencies() {
        return dependencies.getImplementationDependencies();
    }

    @Override
    public Configuration getApiDependencies() {
        return dependencies.getApiDependencies();
    }

    @Override
    public LibraryDependencies getDependencies() {
        return dependencies;
    }

    public void dependencies(Action<? super LibraryDependencies> action) {
        action.execute(dependencies);
    }

    public Configuration getApiElements() {
        return apiElements;
    }

    @Override
    public MainLibraryVariant getMainPublication() {
        return mainVariant;
    }

    @Override
    public ConfigurableFileCollection getPublicHeaders() {
        return publicHeaders;
    }

    @Override
    public void publicHeaders(Action<? super ConfigurableFileCollection> action) {
        action.execute(publicHeaders);
    }

    @Override
    public FileCollection getPublicHeaderDirs() {
        return publicHeadersWithConvention;
    }

    @Override
    public FileTree getPublicHeaderFiles() {
        PatternSet patterns = new PatternSet();
        patterns.include("**/*.h");
        patterns.include("**/*.hpp");
        return publicHeadersWithConvention.getAsFileTree().matching(patterns);
    }

    @Override
    public FileCollection getAllHeaderDirs() {
        return publicHeadersWithConvention.plus(super.getAllHeaderDirs());
    }

    @Override
    public Property<CppBinary> getDevelopmentBinary() {
        return developmentBinary;
    }

    @Override
    public SetProperty<Linkage> getLinkage() {
        return linkage;
    }
}
