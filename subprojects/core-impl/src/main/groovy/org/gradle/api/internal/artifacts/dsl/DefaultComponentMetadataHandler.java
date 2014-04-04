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

import com.google.common.collect.Lists;
import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.ComponentMetadataDetails;
import org.gradle.api.artifacts.IvyModuleDescriptor;
import org.gradle.api.internal.artifacts.ModuleMetadataProcessor;
import org.gradle.api.artifacts.dsl.ComponentMetadataHandler;
import org.gradle.api.internal.artifacts.ivyservice.DefaultIvyModuleDescriptor;
import org.gradle.api.internal.artifacts.ivyservice.ModuleVersionResolveException;
import org.gradle.api.internal.artifacts.metadata.ModuleVersionMetaData;
import org.gradle.api.internal.artifacts.repositories.resolver.ComponentMetadataDetailsAdapter;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.listener.ActionBroadcast;

import java.util.List;

public class DefaultComponentMetadataHandler implements ComponentMetadataHandler, ModuleMetadataProcessor {
    private final Instantiator instantiator;
    private final ActionBroadcast<ComponentMetadataDetails> moduleRules = new ActionBroadcast<ComponentMetadataDetails>();
    private final List<Closure<?>> moduleClosures = Lists.newArrayList();

    public DefaultComponentMetadataHandler(Instantiator instantiator) {
        this.instantiator = instantiator;
    }

    public void eachComponent(Action<? super ComponentMetadataDetails> rule) {
        moduleRules.add(rule);
    }

    public void eachComponent(Closure<?> closure) {
        moduleClosures.add(closure);
    }

    public void process(ModuleVersionMetaData metadata) {
        ComponentMetadataDetails details = instantiator.newInstance(ComponentMetadataDetailsAdapter.class, metadata);
        moduleRules.execute(details);
        executeModuleClosures(metadata, details);
        if (!metadata.getStatusScheme().contains(metadata.getStatus())) {
            throw new ModuleVersionResolveException(metadata.getId(), "Unexpected status '" + metadata.getStatus() + "' specified for %s. Expected one of: " +  metadata.getStatusScheme());
        }
    }

    private void executeModuleClosures(ModuleVersionMetaData metadata, ComponentMetadataDetails details) {
        nextClosure:
        for (Closure<?> closure : moduleClosures) {
            List<Object> args = Lists.newArrayList();
            // TODO: make sure that same argType doesn't occur multiple times?
            for (Class<?> argType : closure.getParameterTypes()) {
                if (argType == ComponentMetadataDetails.class || argType == Object.class) {
                    args.add(details);
                } else if (argType == IvyModuleDescriptor.class) {
                    if (metadata.getIvyMetaData() == null) {
                        continue nextClosure;
                    }
                    args.add(new DefaultIvyModuleDescriptor(metadata.getIvyMetaData().getExtraInfo()));
                } else {
                    throw new GradleException(String.format("Unsupported parameter type for component metadata rule: %s", argType.getName()));
                }
            }
            closure.call(args.toArray());
        }
    }
}
