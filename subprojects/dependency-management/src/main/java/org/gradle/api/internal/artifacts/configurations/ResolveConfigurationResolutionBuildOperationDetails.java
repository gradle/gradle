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

package org.gradle.api.internal.artifacts.configurations;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.gradle.api.internal.artifacts.configurations.ResolveConfigurationDependenciesBuildOperationType.Repository;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;
import org.gradle.api.internal.artifacts.repositories.descriptor.RepositoryDescriptor;
import org.gradle.internal.operations.trace.CustomOperationTraceSerialization;
import org.gradle.util.internal.CollectionUtils;

import javax.annotation.Nullable;
import java.io.File;
import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class ResolveConfigurationResolutionBuildOperationDetails implements ResolveConfigurationDependenciesBuildOperationType.Details, CustomOperationTraceSerialization {

    private final String configurationName;
    private final boolean isScriptConfiguration;
    private final String configurationDescription;
    private final String buildPath;
    private final String projectPath;
    private final boolean isConfigurationVisible;
    private final boolean isConfigurationTransitive;
    private final List<Repository> repositories;

    ResolveConfigurationResolutionBuildOperationDetails(
        String configurationName,
        boolean isScriptConfiguration,
        @Nullable String configurationDescription,
        String buildPath,
        @Nullable String projectPath,
        boolean isConfigurationVisible,
        boolean isConfigurationTransitive,
        List<ResolutionAwareRepository> repositories
    ) {
        this.configurationName = configurationName;
        this.isScriptConfiguration = isScriptConfiguration;
        this.configurationDescription = configurationDescription;
        this.buildPath = buildPath;
        this.projectPath = projectPath;
        this.isConfigurationVisible = isConfigurationVisible;
        this.isConfigurationTransitive = isConfigurationTransitive;
        this.repositories = RepositoryImpl.transform(repositories);
    }

    @Override
    public String getConfigurationName() {
        return configurationName;
    }

    @Nullable
    @Override
    public String getProjectPath() {
        return projectPath;
    }

    @Override
    public boolean isScriptConfiguration() {
        return isScriptConfiguration;
    }

    @Override
    public String getConfigurationDescription() {
        return configurationDescription;
    }

    @Override
    public String getBuildPath() {
        return buildPath;
    }

    @Override
    public boolean isConfigurationVisible() {
        return isConfigurationVisible;
    }

    @Override
    public boolean isConfigurationTransitive() {
        return isConfigurationTransitive;
    }

    @Override
    public List<Repository> getRepositories() {
        return repositories;
    }

    @Override
    public Object getCustomOperationTraceSerializableModel() {
        Map<String, Object> model = new HashMap<>();
        model.put("configurationName", configurationName);
        model.put("scriptConfiguration", isScriptConfiguration);
        model.put("configurationDescription", configurationDescription);
        model.put("buildPath", buildPath);
        model.put("projectPath", projectPath);
        model.put("configurationVisible", isConfigurationVisible);
        model.put("configurationTransitive", isConfigurationTransitive);
        ImmutableList.Builder<Object> repoBuilder = new ImmutableList.Builder<>();
        for (Repository repository : repositories) {
            ImmutableMap.Builder<String, Object> repoMapBuilder = new ImmutableMap.Builder<>();
            repoMapBuilder.put("id", repository.getId());
            repoMapBuilder.put("name", repository.getName());
            repoMapBuilder.put("type", repository.getType());
            ImmutableMap.Builder<String, Object> propertiesMapBuilder = new ImmutableMap.Builder<>();
            for (Map.Entry<String, ?> property : repository.getProperties().entrySet()) {
                Object propertyValue;
                if (property.getValue() instanceof Collection) {
                    ImmutableList.Builder<Object> listBuilder = new ImmutableList.Builder<>();
                    for (Object inner : (Collection<?>) property.getValue()) {
                        doSerialize(inner, listBuilder);
                    }
                    propertyValue = listBuilder.build();
                } else if (property.getValue() instanceof File) {
                    propertyValue = ((File) property.getValue()).getAbsolutePath();
                } else if (property.getValue() instanceof URI) {
                    propertyValue = ((URI) property.getValue()).toASCIIString();
                } else {
                    propertyValue = property.getValue();
                }

                propertiesMapBuilder.put(property.getKey(), propertyValue);
            }
            repoMapBuilder.put("properties", propertiesMapBuilder.build());
            repoBuilder.add(repoMapBuilder.build());
        }
        model.put("repositories", repoBuilder.build());
        return model;
    }

    private void doSerialize(Object value, ImmutableList.Builder<Object> listBuilder) {
        if (value instanceof File) {
            listBuilder.add(((File) value).getAbsolutePath());
        } else if (value instanceof URI) {
            listBuilder.add(((URI) value).toASCIIString());
        } else {
            listBuilder.add(value);
        }
    }

    private static class RepositoryImpl implements Repository {

        private final RepositoryDescriptor descriptor;

        private static List<Repository> transform(List<ResolutionAwareRepository> repositories) {
            return CollectionUtils.collect(repositories, repository -> new RepositoryImpl(repository.getDescriptor()));
        }

        private RepositoryImpl(RepositoryDescriptor descriptor) {
            this.descriptor = descriptor;
        }

        @Override
        public String getId() {
            return descriptor.getId();
        }

        @Override
        public String getType() {
            return descriptor.getType().name();
        }

        @Override
        public String getName() {
            return descriptor.getName();
        }

        @Override
        public Map<String, ?> getProperties() {
            return descriptor.getProperties();
        }
    }

}
