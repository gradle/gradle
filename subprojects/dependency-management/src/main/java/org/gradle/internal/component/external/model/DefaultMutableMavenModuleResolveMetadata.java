/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.component.external.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.ExperimentalFeatures;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.GradlePomModuleDescriptorBuilder;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.model.NamedObjectInstantiator;
import org.gradle.internal.component.external.descriptor.Configuration;

import javax.annotation.Nullable;
import java.util.Collection;

import static org.gradle.internal.component.external.model.DefaultMavenModuleResolveMetadata.JAR_PACKAGINGS;
import static org.gradle.internal.component.external.model.DefaultMavenModuleResolveMetadata.POM_PACKAGING;

public class DefaultMutableMavenModuleResolveMetadata extends AbstractMutableModuleComponentResolveMetadata implements MutableMavenModuleResolveMetadata {

    private final ImmutableAttributesFactory attributesFactory;
    private final NamedObjectInstantiator objectInstantiator;
    private final ExperimentalFeatures experimentalFeatures;

    private String packaging = "jar";
    private boolean relocated;
    private String snapshotTimestamp;
    private ImmutableList<MavenDependencyDescriptor> dependencies;

    public DefaultMutableMavenModuleResolveMetadata(ModuleVersionIdentifier id, ModuleComponentIdentifier componentIdentifier, Collection<MavenDependencyDescriptor> dependencies,
                                                    ImmutableAttributesFactory attributesFactory, NamedObjectInstantiator objectInstantiator,
                                                    ExperimentalFeatures experimentalFeatures) {
        super(attributesFactory, id, componentIdentifier);
        this.dependencies = ImmutableList.copyOf(dependencies);
        this.attributesFactory = attributesFactory;
        this.objectInstantiator = objectInstantiator;
        this.experimentalFeatures = experimentalFeatures;
    }

    DefaultMutableMavenModuleResolveMetadata(MavenModuleResolveMetadata metadata,
                                             ImmutableAttributesFactory attributesFactory, NamedObjectInstantiator objectInstantiator,
                                             ExperimentalFeatures experimentalFeatures) {
        super(metadata);
        this.packaging = metadata.getPackaging();
        this.relocated = metadata.isRelocated();
        this.snapshotTimestamp = metadata.getSnapshotTimestamp();
        this.dependencies = metadata.getDependencies();
        this.attributesFactory = attributesFactory;
        this.objectInstantiator = objectInstantiator;
        this.experimentalFeatures = experimentalFeatures;
    }

    @Override
    public MavenModuleResolveMetadata asImmutable() {
        return new DefaultMavenModuleResolveMetadata(this, attributesFactory, objectInstantiator, experimentalFeatures);
    }

    @Override
    protected ImmutableMap<String, Configuration> getConfigurationDefinitions() {
        return GradlePomModuleDescriptorBuilder.MAVEN2_CONFIGURATIONS;
    }

    @Nullable
    @Override
    public String getSnapshotTimestamp() {
        return snapshotTimestamp;
    }

    @Override
    public void setSnapshotTimestamp(@Nullable String snapshotTimestamp) {
        this.snapshotTimestamp = snapshotTimestamp;
    }

    @Override
    public boolean isRelocated() {
        return relocated;
    }

    @Override
    public void setRelocated(boolean relocated) {
        this.relocated = relocated;
    }

    @Override
    public String getPackaging() {
        return packaging;
    }

    @Override
    public void setPackaging(String packaging) {
        this.packaging = packaging;
    }

    @Override
    public boolean isPomPackaging() {
        return POM_PACKAGING.equals(packaging);
    }

    @Override
    public boolean isKnownJarPackaging() {
        return JAR_PACKAGINGS.contains(packaging);
    }

    @Override
    public ImmutableList<MavenDependencyDescriptor> getDependencies() {
        return dependencies;
    }
}
