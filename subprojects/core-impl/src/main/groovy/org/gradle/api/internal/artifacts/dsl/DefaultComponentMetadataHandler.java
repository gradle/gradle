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
package org.gradle.api.internal.artifacts.dsl;

import org.gradle.api.Action;
import org.gradle.api.artifacts.ComponentMetadataDetails;
import org.gradle.api.internal.artifacts.ModuleMetadataProcessor;
import org.gradle.api.artifacts.dsl.ComponentMetadataHandler;
import org.gradle.api.internal.artifacts.ivyservice.ModuleVersionResolveException;
import org.gradle.api.internal.artifacts.metadata.ModuleVersionMetaData;
import org.gradle.api.internal.artifacts.repositories.resolver.ComponentMetadataDetailsAdapter;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.listener.ActionBroadcast;

public class DefaultComponentMetadataHandler implements ComponentMetadataHandler, ModuleMetadataProcessor {
    private final Instantiator instantiator;
    private final ActionBroadcast<ComponentMetadataDetails> moduleRules = new ActionBroadcast<ComponentMetadataDetails>();

    public DefaultComponentMetadataHandler(Instantiator instantiator) {
        this.instantiator = instantiator;
    }

    public void eachComponent(Action<? super ComponentMetadataDetails> rule) {
        moduleRules.add(rule);
    }

    public void process(ModuleVersionMetaData metadata) {
        ComponentMetadataDetails details = instantiator.newInstance(ComponentMetadataDetailsAdapter.class, metadata);
        moduleRules.execute(details);
        if (!metadata.getStatusScheme().contains(metadata.getStatus())) {
            throw new ModuleVersionResolveException(metadata.getId(), "Unexpected status '" + metadata.getStatus() + "' specified for %s. Expected one of: " +  metadata.getStatusScheme());
        }
    }
}
