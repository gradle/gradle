/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.internal.artifacts;

import org.gradle.StartParameter;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.artifacts.dsl.CapabilityNotationParser;
import org.gradle.api.internal.artifacts.dsl.CapabilityNotationParserFactory;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactoryInternal;
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheLockingAccessCoordinator;
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheMetadata;
import org.gradle.api.internal.artifacts.transform.TransformStepNodeDependencyResolver;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.filestore.DefaultArtifactIdentifierFileStore;
import org.gradle.api.internal.notations.ClientModuleNotationParserFactory;
import org.gradle.api.internal.notations.DependencyConstraintNotationParser;
import org.gradle.api.internal.notations.DependencyNotationParser;
import org.gradle.api.internal.notations.ProjectDependencyFactory;
import org.gradle.api.internal.runtimeshaded.RuntimeShadedJarFactory;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.internal.installation.CurrentGradleInstallation;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.resource.cached.ByUrlCachedExternalResourceIndex;
import org.gradle.internal.resource.cached.DefaultExternalResourceFileStore;
import org.gradle.internal.resource.cached.ExternalResourceFileStore;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.util.internal.BuildCommencedTimeProvider;
import org.gradle.util.internal.SimpleMapInterner;

/**
 * The set of dependency management services that are created per project.
 */
class DependencyManagementProjectScopeServices {
    void configure(ServiceRegistration registration) {
        registration.add(DefaultExternalResourceFileStore.Factory.class);
        registration.add(DefaultArtifactIdentifierFileStore.Factory.class);
        registration.add(TransformStepNodeDependencyResolver.class);
    }

    /** This is needed in order to get the {@code taskDependencyFactory} from the current project. */
    DefaultProjectDependencyFactory createProjectDependencyFactory(
        Instantiator instantiator,
        StartParameter startParameter,
        ImmutableAttributesFactory attributesFactory,
        TaskDependencyFactory taskDependencyFactory
    ) {
        NotationParser<Object, Capability> capabilityNotationParser = new CapabilityNotationParserFactory(false).create();
        return new DefaultProjectDependencyFactory(instantiator, startParameter.isBuildProjectDependencies(), capabilityNotationParser, attributesFactory, taskDependencyFactory);
    }

    DependencyFactoryInternal createDependencyFactory(
        Instantiator instantiator,
        DefaultProjectDependencyFactory factory,
        ClassPathRegistry classPathRegistry,
        CurrentGradleInstallation currentGradleInstallation,
        FileCollectionFactory fileCollectionFactory,
        RuntimeShadedJarFactory runtimeShadedJarFactory,
        ImmutableAttributesFactory attributesFactory,
        SimpleMapInterner stringInterner,
        CapabilityNotationParser capabilityNotationParser
    ) {
        ProjectDependencyFactory projectDependencyFactory = new ProjectDependencyFactory(factory);

        return new DefaultDependencyFactory(
                instantiator,
                DependencyNotationParser.create(instantiator, factory, classPathRegistry, fileCollectionFactory, runtimeShadedJarFactory, currentGradleInstallation, stringInterner),
                DependencyConstraintNotationParser.parser(instantiator, factory, stringInterner, attributesFactory),
                new ClientModuleNotationParserFactory(instantiator, stringInterner).create(),
                capabilityNotationParser,
                projectDependencyFactory,
                attributesFactory);
    }

    private ByUrlCachedExternalResourceIndex prepareArtifactUrlCachedResolutionIndex(BuildCommencedTimeProvider timeProvider, ArtifactCacheLockingAccessCoordinator cacheAccessCoordinator, ExternalResourceFileStore externalResourceFileStore, ArtifactCacheMetadata artifactCacheMetadata) {
        return new ByUrlCachedExternalResourceIndex(
            "resource-at-url",
            timeProvider,
            cacheAccessCoordinator,
            externalResourceFileStore.getFileAccessTracker(),
            artifactCacheMetadata.getCacheDir().toPath()
        );
    }
}
