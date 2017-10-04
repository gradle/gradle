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
import com.google.common.collect.ImmutableSet;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.dsl.ArtifactFile;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.GradlePomModuleDescriptorBuilder;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.component.external.descriptor.Artifact;
import org.gradle.internal.component.external.descriptor.Configuration;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.Exclude;
import org.gradle.internal.component.model.VariantMetadata;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.gradle.internal.component.external.model.DefaultMavenModuleResolveMetadata.JAR_PACKAGINGS;
import static org.gradle.internal.component.external.model.DefaultMavenModuleResolveMetadata.POM_PACKAGING;

public class DefaultMutableMavenModuleResolveMetadata extends AbstractMutableModuleComponentResolveMetadata implements MutableMavenModuleResolveMetadata {
    private String packaging = "jar";
    private boolean relocated;
    private String snapshotTimestamp;
    private List<MutableVariantImpl> newVariants;
    private ImmutableList<? extends ComponentVariant> variants;

    /**
     * Creates default metadata for a Maven module with no POM.
     */
    public static DefaultMutableMavenModuleResolveMetadata missing(ModuleVersionIdentifier id, ModuleComponentIdentifier componentIdentifier) {
        DefaultMutableMavenModuleResolveMetadata metadata = new DefaultMutableMavenModuleResolveMetadata(id, componentIdentifier);
        metadata.setMissing(true);
        return metadata;
    }

    public DefaultMutableMavenModuleResolveMetadata(ModuleVersionIdentifier id, ModuleComponentIdentifier componentIdentifier) {
        this(id, componentIdentifier, ImmutableList.<DependencyMetadata>of());
    }

    public DefaultMutableMavenModuleResolveMetadata(ModuleVersionIdentifier id, ModuleComponentIdentifier componentIdentifier, Collection<? extends DependencyMetadata> dependencies) {
        super(id, componentIdentifier, ImmutableList.copyOf(dependencies));
    }

    DefaultMutableMavenModuleResolveMetadata(MavenModuleResolveMetadata metadata) {
        super(metadata);
        this.packaging = metadata.getPackaging();
        this.relocated = metadata.isRelocated();
        this.snapshotTimestamp = metadata.getSnapshotTimestamp();
        variants = metadata.getVariants();
    }

    @Override
    public MavenModuleResolveMetadata asImmutable() {
        return new DefaultMavenModuleResolveMetadata(this);
    }

    @Override
    protected List<Artifact> getArtifacts() {
        return ImmutableList.of(new Artifact(new DefaultIvyArtifactName(getComponentId().getModule(), "jar", "jar"), ImmutableSet.of("compile")));
    }

    @Override
    protected Map<String, Configuration> getConfigurationDefinitions() {
        return GradlePomModuleDescriptorBuilder.MAVEN2_CONFIGURATIONS;
    }

    @Override
    protected List<Exclude> getExcludes() {
        return ImmutableList.of();
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
    public MutableComponentVariant addVariant(String variantName, ImmutableAttributes attributes) {
        MutableVariantImpl variant = new MutableVariantImpl(variantName, attributes);
        if (newVariants == null) {
            newVariants = new ArrayList<MutableVariantImpl>();
        }
        newVariants.add(variant);
        return variant;
    }

    @Override
    public ImmutableList<? extends ComponentVariant> getVariants() {
        if (variants == null && newVariants == null) {
            return ImmutableList.of();
        }
        if (variants != null && newVariants == null) {
            return variants;
        }
        ImmutableList.Builder<ComponentVariant> builder = new ImmutableList.Builder<ComponentVariant>();
        if (variants != null) {
            builder.addAll(variants);
        }
        for (MutableVariantImpl variant : newVariants) {
            builder.add(new ImmutableVariantImpl(getComponentId(), variant.name, variant.attributes, ImmutableList.copyOf(variant.files)));
        }
        return builder.build();
    }

    private static class MutableVariantImpl implements MutableComponentVariant {
        private final String name;
        private final ImmutableAttributes attributes;
        private final List<FileImpl> files = new ArrayList<FileImpl>();

        MutableVariantImpl(String name, ImmutableAttributes attributes) {
            this.name = name;
            this.attributes = attributes;
        }

        @Override
        public void addFile(String name, String uri) {
            files.add(new FileImpl(name, uri));
        }
    }

    private static class FileImpl implements ComponentVariant.File {
        private final String name;
        private final String uri;

        FileImpl(String name, String uri) {
            this.name = name;
            this.uri = uri;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getUri() {
            return uri;
        }
    }

    private static class ImmutableVariantImpl implements ComponentVariant, VariantMetadata {
        private final ModuleComponentIdentifier componentId;
        private final String name;
        private final ImmutableAttributes attributes;
        private final ImmutableList<FileImpl> files;

        ImmutableVariantImpl(ModuleComponentIdentifier componentId, String name, ImmutableAttributes attributes, ImmutableList<FileImpl> files) {
            this.componentId = componentId;
            this.name = name;
            this.attributes = attributes;
            this.files = files;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public DisplayName asDescribable() {
            return Describables.of(componentId, "variant", name);
        }

        @Override
        public ImmutableAttributes getAttributes() {
            return attributes;
        }

        @Override
        public ImmutableList<? extends File> getFiles() {
            return files;
        }

        @Override
        public Set<? extends ComponentArtifactMetadata> getArtifacts() {
            Set<ComponentArtifactMetadata> artifacts = new LinkedHashSet<ComponentArtifactMetadata>(files.size());
            for (ComponentVariant.File file : files) {
                ArtifactFile names = new ArtifactFile(file.getUri(), componentId.getVersion());
                artifacts.add(new DefaultModuleComponentArtifactMetadata(componentId, new DefaultIvyArtifactName(names.getName(), names.getExtension(), names.getExtension(), names.getClassifier())));
            }
            return artifacts;
        }

    }
}
