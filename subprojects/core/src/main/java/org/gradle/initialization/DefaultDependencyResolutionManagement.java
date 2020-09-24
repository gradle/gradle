/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.initialization;

import groovy.lang.Closure;
import org.apache.commons.compress.utils.Lists;
import org.gradle.api.Action;
import org.gradle.api.ActionConfiguration;
import org.gradle.api.artifacts.ComponentMetadataDetails;
import org.gradle.api.artifacts.ComponentMetadataRule;
import org.gradle.api.artifacts.dsl.ComponentMetadataHandler;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.internal.artifacts.CrossProjectResolutionServices;
import org.gradle.api.internal.artifacts.DependencyResolutionServices;

import java.util.List;

public class DefaultDependencyResolutionManagement implements DependencyResolutionManagementInternal {
    private final CrossProjectResolutionServices services;
    private final List<Action<? super ComponentMetadataHandler>> componentMetadataRulesActions = Lists.newArrayList();

    public DefaultDependencyResolutionManagement(CrossProjectResolutionServices services) {
        this.services = services;
    }

    @Override
    public void repositories(Action<? super RepositoryHandler> repositoryConfiguration) {
        repositoryConfiguration.execute(getDependencyResolutionServices().getResolveRepositoryHandler());
    }

    private DependencyResolutionServices getDependencyResolutionServices() {
        return services.getDependencyResolutionServices();
    }

    @Override
    public void components(Action<? super ComponentMetadataHandler> registration) {
        componentMetadataRulesActions.add(registration);
    }

    @Override
    public ComponentMetadataHandler getComponents() {
        return new ComponentMetadataRulesRegistar();
    }

    @Override
    public RepositoryHandler getRepositoryHandler() {
        return getDependencyResolutionServices().getResolveRepositoryHandler();
    }

    @Override
    public void applyRules(ComponentMetadataHandler target) {
        for (Action<? super ComponentMetadataHandler> rule : componentMetadataRulesActions) {
            rule.execute(target);
        }
    }

    private class ComponentMetadataRulesRegistar implements ComponentMetadataHandler {
        @Override
        public ComponentMetadataHandler all(Action<? super ComponentMetadataDetails> rule) {
            components(h -> h.all(rule));
            return this;
        }

        @Override
        public ComponentMetadataHandler all(Closure<?> rule) {
            components(h -> h.all(rule));
            return this;
        }

        @Override
        public ComponentMetadataHandler all(Object ruleSource) {
            components(h -> h.all(ruleSource));
            return this;
        }

        @Override
        public ComponentMetadataHandler all(Class<? extends ComponentMetadataRule> rule) {
            components(h -> h.all(rule));
            return this;
        }

        @Override
        public ComponentMetadataHandler all(Class<? extends ComponentMetadataRule> rule, Action<? super ActionConfiguration> configureAction) {
            components(h -> h.all(rule, configureAction));
            return this;
        }

        @Override
        public ComponentMetadataHandler withModule(Object id, Action<? super ComponentMetadataDetails> rule) {
            components(h -> h.withModule(id, rule));
            return this;
        }

        @Override
        public ComponentMetadataHandler withModule(Object id, Closure<?> rule) {
            components(h -> h.withModule(id, rule));
            return this;
        }

        @Override
        public ComponentMetadataHandler withModule(Object id, Object ruleSource) {
            components(h -> h.withModule(id, ruleSource));
            return this;
        }

        @Override
        public ComponentMetadataHandler withModule(Object id, Class<? extends ComponentMetadataRule> rule) {
            components(h -> h.withModule(id, rule));
            return this;
        }

        @Override
        public ComponentMetadataHandler withModule(Object id, Class<? extends ComponentMetadataRule> rule, Action<? super ActionConfiguration> configureAction) {
            components(h -> h.withModule(id, rule, configureAction));
            return this;
        }
    }
}
