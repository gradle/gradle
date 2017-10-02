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

package org.gradle.internal.component.external.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.GradlePomModuleDescriptorBuilder;
import org.gradle.internal.component.external.descriptor.Artifact;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.Exclude;
import org.gradle.internal.component.model.ModuleSource;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;

public class DefaultMavenModuleResolveMetadata extends AbstractModuleComponentResolveMetadata implements MavenModuleResolveMetadata {
    public static final String POM_PACKAGING = "pom";
    public static final Collection<String> JAR_PACKAGINGS = Arrays.asList("jar", "ejb", "bundle", "maven-plugin", "eclipse-plugin");
    private final String packaging;
    private final boolean relocated;
    private final String snapshotTimestamp;
    private final ImmutableList<? extends ComponentVariant> variants;

    DefaultMavenModuleResolveMetadata(MutableMavenModuleResolveMetadata metadata) {
        super(metadata,
            GradlePomModuleDescriptorBuilder.MAVEN2_CONFIGURATIONS,
            ImmutableList.of(new Artifact(new DefaultIvyArtifactName(metadata.getComponentId().getModule(), "jar", "jar"), ImmutableSet.of("compile"))),
            ImmutableList.<Exclude>of());
        packaging = metadata.getPackaging();
        relocated = metadata.isRelocated();
        snapshotTimestamp = metadata.getSnapshotTimestamp();
        variants = metadata.getVariants();
    }

    private DefaultMavenModuleResolveMetadata(DefaultMavenModuleResolveMetadata metadata, ModuleSource source) {
        super(metadata, source);
        packaging = metadata.getPackaging();
        relocated = metadata.isRelocated();
        snapshotTimestamp = metadata.getSnapshotTimestamp();
        variants = metadata.getVariants();
    }

    @Override
    public DefaultMavenModuleResolveMetadata withSource(ModuleSource source) {
        return new DefaultMavenModuleResolveMetadata(this, source);
    }

    @Override
    public MutableMavenModuleResolveMetadata asMutable() {
        return new DefaultMutableMavenModuleResolveMetadata(this);
    }

    public String getPackaging() {
        return packaging;
    }

    public boolean isRelocated() {
        return relocated;
    }

    public boolean isPomPackaging() {
        return POM_PACKAGING.equals(packaging);
    }

    public boolean isKnownJarPackaging() {
        return JAR_PACKAGINGS.contains(packaging);
    }

    @Nullable
    public String getSnapshotTimestamp() {
        return snapshotTimestamp;
    }

    @Override
    public ImmutableList<? extends ComponentVariant> getVariants() {
        return variants;
    }
}
