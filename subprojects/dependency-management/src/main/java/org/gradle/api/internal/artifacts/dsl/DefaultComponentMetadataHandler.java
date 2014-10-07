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
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.artifacts.ComponentMetadataDetails;
import org.gradle.api.artifacts.dsl.ComponentMetadataHandler;
import org.gradle.api.artifacts.ivy.IvyModuleDescriptor;
import org.gradle.api.internal.artifacts.ComponentMetadataProcessor;
import org.gradle.api.internal.artifacts.ivyservice.DefaultIvyModuleDescriptor;
import org.gradle.api.internal.artifacts.repositories.resolver.ComponentMetadataDetailsAdapter;
import org.gradle.internal.component.external.model.IvyModuleResolveMetaData;
import org.gradle.internal.component.external.model.ModuleComponentResolveMetaData;
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetaData;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.gradle.listener.ActionBroadcast;

import java.util.Arrays;
import java.util.List;

public class DefaultComponentMetadataHandler implements ComponentMetadataHandler, ComponentMetadataProcessor {
    private final Instantiator instantiator;
    private final ActionBroadcast<ComponentMetadataDetails> ruleActions = new ActionBroadcast<ComponentMetadataDetails>();
    private final List<Closure<?>> ruleClosures = Lists.newArrayList();

    public DefaultComponentMetadataHandler(Instantiator instantiator) {
        this.instantiator = instantiator;
    }

    public void eachComponent(Action<? super ComponentMetadataDetails> rule) {
        ruleActions.add(rule);
    }

    public void eachComponent(Closure<?> closure) {
        ruleClosures.add(closure);
    }

    public void processMetadata(MutableModuleComponentResolveMetaData metadata) {
        ComponentMetadataDetails details = instantiator.newInstance(ComponentMetadataDetailsAdapter.class, metadata);
        ruleActions.execute(details);
        executeRuleClosures(metadata, details);
        if (!metadata.getStatusScheme().contains(metadata.getStatus())) {
            throw new ModuleVersionResolveException(metadata.getId(), "Unexpected status '" + metadata.getStatus() + "' specified for %s. Expected one of: " +  metadata.getStatusScheme());
        }
    }

    private void executeRuleClosures(ModuleComponentResolveMetaData metadata, ComponentMetadataDetails details) {
        for (Closure<?> closure : ruleClosures) {
            executeRuleClosure(metadata, details, closure);
        }
    }

    private void executeRuleClosure(ModuleComponentResolveMetaData metadata, ComponentMetadataDetails details, Closure<?> closure) {
        Class<?>[] parameterTypes = closure.getParameterTypes();
        if (parameterTypes.length == 0) {
            throw new InvalidUserCodeException("A component metadata rule needs to have at least one parameter.");
        }

        List<Object> args = Lists.newArrayList();

        if (parameterTypes[0].isAssignableFrom(ComponentMetadataDetails.class)) {
            args.add(details);
        } else {
            throw new InvalidUserCodeException(
                    String.format("First parameter of a component metadata rule needs to be of type '%s'.",
                            ComponentMetadataDetails.class.getSimpleName()));
        }

        for (Class<?> parameterType : Arrays.asList(parameterTypes).subList(1, parameterTypes.length)) {
            if (parameterType == IvyModuleDescriptor.class) {
                if (!(metadata instanceof IvyModuleResolveMetaData)) {
                    return;
                }
                IvyModuleResolveMetaData ivyMetadata = (IvyModuleResolveMetaData) metadata;
                args.add(new DefaultIvyModuleDescriptor(ivyMetadata.getExtraInfo(), ivyMetadata.getBranch(), ivyMetadata.getStatus()));
            } else {
                throw new InvalidUserCodeException(String.format("Unsupported parameter type for component metadata rule: %s", parameterType.getName()));
            }
        }
        closure.call(args.toArray());
    }
}
