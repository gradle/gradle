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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve;

import org.gradle.api.artifacts.ComponentMetadataSupplierDetails;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.ComponentMetadataProcessorFactory;
import org.gradle.api.internal.artifacts.ivyservice.CacheExpirationControl;
import org.gradle.api.internal.attributes.AttributesFactory;
import org.gradle.internal.action.InstantiatingAction;
import org.gradle.internal.component.external.model.ExternalModuleComponentGraphResolveState;
import org.gradle.internal.resolve.caching.ComponentMetadataSupplierRuleExecutor;
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult;
import org.jspecify.annotations.Nullable;

public interface ModuleComponentResolveState extends Versioned {
    ModuleComponentIdentifier getId();

    BuildableModuleComponentMetaDataResolveResult<ExternalModuleComponentGraphResolveState> resolve();

    ComponentMetadataProcessorFactory getComponentMetadataProcessorFactory();

    AttributesFactory getAttributesFactory();

    @Nullable
    InstantiatingAction<ComponentMetadataSupplierDetails> getComponentMetadataSupplier();

    ComponentMetadataSupplierRuleExecutor getComponentMetadataSupplierExecutor();

    CacheExpirationControl getCacheExpirationControl();
}
