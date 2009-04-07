/*
 * Copyright 2007-2009 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies;

import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor;
import org.apache.ivy.core.module.id.ModuleId;
import org.apache.ivy.plugins.conflict.LatestConflictManager;
import org.apache.ivy.plugins.latest.LatestRevisionStrategy;
import org.apache.ivy.plugins.matcher.ExactPatternMatcher;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ExcludeRule;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.DefaultExcludeRuleConverter;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.ExcludeRuleConverter;

import java.util.*;

/**
 * @author Hans Dockter
 */
public class DefaultDependenciesToModuleDescriptorConverter implements DependenciesToModuleDescriptorConverter {
    private DependencyDescriptorFactory dependencyDescriptorFactory = new DefaultDependencyDescriptorFactory();
    private ExcludeRuleConverter excludeRuleConverter = new DefaultExcludeRuleConverter();

    public void addDependencyDescriptors(DefaultModuleDescriptor moduleDescriptor, Set<Configuration> configurations,
                                         Map clientModuleRegistry) {
        assert !configurations.isEmpty();
        addDependencies(moduleDescriptor, configurations, clientModuleRegistry);
        addExcludeRules(moduleDescriptor, configurations);
        addConflictManager(moduleDescriptor);
    }

    private void addDependencies(DefaultModuleDescriptor moduleDescriptor, Set<Configuration> configurations, Map clientModuleRegistry) {
        for (Configuration configuration : configurations) {
            for (Dependency dependency : configuration.getDependencies()) {
                moduleDescriptor.addDependency(dependencyDescriptorFactory.createDependencyDescriptor(
                    configuration.getName(), moduleDescriptor, dependency, clientModuleRegistry));    
            }
        }
    }

//    private Map<Dependency, String> normalizeDependencies(List<Configuration> configurations) {
//        Map<Dependency, String> normalizedDependencies = new HashMap<Dependency, String>();
//        Iterator<Configuration> iterator = new ReverseIterator<Configuration>(configurations);
//        while (iterator.hasNext()) {
//            Configuration configuration = iterator.next();
//            for (Dependency dependency : configuration.getDependencies()) {
//                normalizedDependencies.remove(dependency);
//                normalizedDependencies.put(dependency, configuration.getName());
//            }
//        }
//        return normalizedDependencies;
//    }

    private void addExcludeRules(DefaultModuleDescriptor moduleDescriptor, Set<Configuration> configurations) {
        for (Configuration configuration : configurations) {
            for (ExcludeRule excludeRule : configuration.getExcludeRules()) {
                moduleDescriptor.addExcludeRule(excludeRuleConverter.createExcludeRule(excludeRule));
            }
        }
    }

    private void addConflictManager(DefaultModuleDescriptor moduleDescriptor) {
        moduleDescriptor.addConflictManager(new ModuleId(ExactPatternMatcher.ANY_EXPRESSION,
                ExactPatternMatcher.ANY_EXPRESSION), ExactPatternMatcher.INSTANCE,
                new LatestConflictManager(new LatestRevisionStrategy()));
    }

    public DependencyDescriptorFactory getDependencyDescriptorFactory() {
        return dependencyDescriptorFactory;
    }

    public void setDependencyDescriptorFactory(DependencyDescriptorFactory dependencyDescriptorFactory) {
        this.dependencyDescriptorFactory = dependencyDescriptorFactory;
    }

    public ExcludeRuleConverter getExcludeRuleConverter() {
        return excludeRuleConverter;
    }

    public void setExcludeRuleConverter(ExcludeRuleConverter excludeRuleConverter) {
        this.excludeRuleConverter = excludeRuleConverter;
    }

    public class ReverseIterator<E> implements Iterator<E> {
        private ListIterator<E> iter;
        
        public ReverseIterator(List list) {
            iter = list.listIterator(list.size());
        }

        public boolean hasNext() {
            return iter.hasPrevious();
        }

        public E next() {
            return iter.previous();
        }

        public void remove() {
            iter.remove();
        }
    }
}
