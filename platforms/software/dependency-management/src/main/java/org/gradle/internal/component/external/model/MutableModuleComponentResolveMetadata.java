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
package org.gradle.internal.component.external.model;

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.AttributesFactory;
import org.gradle.internal.component.model.ModuleSources;
import org.gradle.internal.component.model.MutableModuleSources;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;

public interface MutableModuleComponentResolveMetadata {
    /**
     * The identifier for this component
     */
    ModuleComponentIdentifier getId();

    /**
     * The module version associated with this module.
     */
    ModuleVersionIdentifier getModuleVersionId();

    /**
     * Creates an immutable copy of this meta-data.
     */
    ModuleComponentResolveMetadata asImmutable();

    /**
     * Sets the component id and legacy module version id
     */
    void setId(ModuleComponentIdentifier componentId);

    boolean isMissing();
    void setMissing(boolean missing);

    boolean isChanging();
    void setChanging(boolean changing);

    String getStatus();
    void setStatus(String status);

    List<String> getStatusScheme();
    void setStatusScheme(List<String> statusScheme);

    MutableModuleSources getSources();

    void setSources(ModuleSources moduleSources);

    MutableComponentVariant addVariant(MutableComponentVariant variant);

    MutableComponentVariant addVariant(String variantName, ImmutableAttributes attributes);

    AttributeContainer getAttributes();

    void setAttributes(AttributeContainer attributes);

    boolean isExternalVariant();

    void setExternalVariant(boolean externalVariant);

    boolean isComponentMetadataRuleCachingEnabled();

    void setComponentMetadataRuleCachingEnabled(boolean componentMetadataRuleCachingEnabled);

    /**
     * Creates an artifact for this module. Does not mutate this metadata.
     */
    ModuleComponentArtifactMetadata artifact(String type, @Nullable String extension, @Nullable String classifier);

    AttributesFactory getAttributesFactory();

    /**
     * Returns the metadata rules container for this module
     */
    VariantMetadataRules getVariantMetadataRules();

    /**
     * Declares that this component belongs to a virtual platform.
     * @param platform the identifier of the virtual platform
     */
    void belongsTo(VirtualComponentIdentifier platform);

    @Nullable
    Set<? extends VirtualComponentIdentifier> getPlatformOwners();

    List<? extends MutableComponentVariant> getMutableVariants();
}
