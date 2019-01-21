/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.ide.visualstudio.internal;

import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.DefaultNamedDomainObjectSet;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.ide.visualstudio.VisualStudioProject;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.plugins.ide.internal.IdeArtifactRegistry;
import org.gradle.util.VersionNumber;

public class VisualStudioProjectRegistry extends DefaultNamedDomainObjectSet<DefaultVisualStudioProject> {
    private final FileResolver fileResolver;
    private final IdeArtifactRegistry ideArtifactRegistry;
    private final ProviderFactory providerFactory;

    public VisualStudioProjectRegistry(FileResolver fileResolver, Instantiator instantiator, IdeArtifactRegistry ideArtifactRegistry, CollectionCallbackActionDecorator collectionCallbackActionDecorator, ProviderFactory providerFactory) {
        super(DefaultVisualStudioProject.class, instantiator, collectionCallbackActionDecorator);
        this.fileResolver = fileResolver;
        this.ideArtifactRegistry = ideArtifactRegistry;
        this.providerFactory = providerFactory;
    }

    public VisualStudioProjectConfiguration getProjectConfiguration(VisualStudioTargetBinary targetBinary) {
        String projectName = targetBinary.getVisualStudioProjectName();
        return getByName(projectName).getConfiguration(targetBinary);
    }

    public VisualStudioProjectConfiguration addProjectConfiguration(VisualStudioTargetBinary nativeBinary) {
        DefaultVisualStudioProject project = getOrCreateProject(nativeBinary.getVisualStudioProjectName(), nativeBinary.getComponentName(), providerFactory.provider(() -> nativeBinary.getVisualStudioVersion()), providerFactory.provider(() -> nativeBinary.getSdkVersion()));
        VisualStudioProjectConfiguration configuration = createVisualStudioProjectConfiguration(project, nativeBinary, nativeBinary.getVisualStudioConfigurationName());
        project.addConfiguration(nativeBinary, configuration);
        return configuration;
    }

    private VisualStudioProjectConfiguration createVisualStudioProjectConfiguration(VisualStudioProject project, VisualStudioTargetBinary nativeBinary, String configuration) {
        return getInstantiator().newInstance(VisualStudioProjectConfiguration.class, project, configuration, nativeBinary);
    }

    private DefaultVisualStudioProject getOrCreateProject(String vsProjectName, String componentName, Provider<VersionNumber> visualStudioVersion, Provider<VersionNumber> sdkVersion) {
        DefaultVisualStudioProject vsProject = findByName(vsProjectName);
        if (vsProject == null) {
            vsProject = getInstantiator().newInstance(DefaultVisualStudioProject.class, vsProjectName, componentName, visualStudioVersion, sdkVersion, fileResolver, getInstantiator());
            add(vsProject);
            ideArtifactRegistry.registerIdeProject(vsProject.getPublishArtifact());
        }
        return vsProject;
    }
}
