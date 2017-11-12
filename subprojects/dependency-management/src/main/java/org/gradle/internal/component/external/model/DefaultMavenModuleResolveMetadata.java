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
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.Usage;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.DisambiguationRule;
import org.gradle.api.internal.attributes.EmptySchema;
import org.gradle.api.internal.attributes.MultipleCandidatesResult;
import org.gradle.api.internal.model.NamedObjectInstantiator;
import org.gradle.internal.Cast;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.ModuleSource;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

public class DefaultMavenModuleResolveMetadata extends AbstractModuleComponentResolveMetadata implements MavenModuleResolveMetadata {

    public static final String POM_PACKAGING = "pom";
    public static final Collection<String> JAR_PACKAGINGS = Arrays.asList("jar", "ejb", "bundle", "maven-plugin", "eclipse-plugin");
    private static final PreferJavaRuntimeVariant SCHEMA_DEFAULT_JAVA_VARIANTS = new PreferJavaRuntimeVariant();

    private final String packaging;
    private final boolean relocated;
    private final String snapshotTimestamp;
    private final ImmutableList<? extends ComponentVariant> variants;
    private final ImmutableList<? extends ConfigurationMetadata> graphVariants;

    DefaultMavenModuleResolveMetadata(MutableMavenModuleResolveMetadata metadata) {
        super(metadata);
        packaging = metadata.getPackaging();
        relocated = metadata.isRelocated();
        snapshotTimestamp = metadata.getSnapshotTimestamp();
        variants = metadata.getVariants();
        graphVariants = metadata.getVariantsForGraphTraversal();
    }

    private DefaultMavenModuleResolveMetadata(DefaultMavenModuleResolveMetadata metadata, ModuleSource source) {
        super(metadata, source);
        packaging = metadata.packaging;
        relocated = metadata.relocated;
        snapshotTimestamp = metadata.snapshotTimestamp;
        variants = metadata.variants;
        graphVariants = metadata.graphVariants;
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
    public ImmutableList<? extends ConfigurationMetadata> getVariantsForGraphTraversal() {
        return graphVariants;
    }

    @Override
    public ImmutableList<? extends ComponentVariant> getVariants() {
        return variants;
    }

    @Nullable
    @Override
    public AttributesSchemaInternal getAttributesSchema() {
        return SCHEMA_DEFAULT_JAVA_VARIANTS;
    }

    /**
     * When no consumer attributes are provided, prefer the Java runtime variant over the API variant.
     *
     * Gradle has long assumed that, by default, consumers of a maven repository require the _runtime_ variant
     * of the published library.
     * The following disambiguation rule encodes this assumption for the case where a java library is published
     * with variants using Gradle module metadata. This will allow us to migrate to consuming the new module
     * metadata format by default without breaking a bunch of consumers that depend on this assumption,
     * declaring no preference for a particular variant.
     */
    private static class PreferJavaRuntimeVariant extends EmptySchema {
        private static final NamedObjectInstantiator INSTANTIATOR = NamedObjectInstantiator.INSTANCE;
        private static final Usage JAVA_API = INSTANTIATOR.named(Usage.class, Usage.JAVA_API);
        private static final Usage JAVA_RUNTIME = INSTANTIATOR.named(Usage.class, Usage.JAVA_RUNTIME);
        private static final Set<Usage> DEFAULT_JAVA_USAGES = ImmutableSet.of(JAVA_API, JAVA_RUNTIME);

        @Override
        public DisambiguationRule<Object> disambiguationRules(Attribute<?> attribute) {
            if (attribute.getType().equals(Usage.class)) {
                return Cast.uncheckedCast(new DisambiguationRule<Usage>() {
                    public void execute(MultipleCandidatesResult<Usage> details) {
                        if (details.getConsumerValue() == null) {
                            if (details.getCandidateValues().equals(DEFAULT_JAVA_USAGES)) {
                                details.closestMatch(JAVA_RUNTIME);
                            }
                        }
                    }
                });
            }
            return super.disambiguationRules(attribute);
        }
    }
}
