/*
 * Copyright 2007-2008 the original author or authors.
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

package org.gradle.api.internal.dependencies;

import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.descriptor.ExcludeRule;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.DependencyManager;
import org.gradle.api.dependencies.Dependency;
import org.gradle.util.WrapUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Hans Dockter
 */
public class DependencyDescriptorFactory {
    public DependencyDescriptor createDescriptor(ModuleDescriptor parent, String descriptor, boolean force,
                                                 boolean transitive, boolean changing, Set<String> confs,
                                          List<ExcludeRule> excludeRules) {
        return createDescriptor(parent, descriptor, force, transitive, changing, confs, excludeRules, new HashMap());
    }

    public DependencyDescriptor createDescriptor(ModuleDescriptor parent, String descriptor, boolean force,
                                                 boolean transitive, boolean changing, Set<String> confs,
                                          List<ExcludeRule> excludeRules, Map extraAttributes) {
        String[] dependencyParts = descriptor.split(":");
        Map allExtraAttributes = (dependencyParts.length == 4 ? WrapUtil.toMap(DependencyManager.CLASSIFIER, dependencyParts[3]) :
            new HashMap());
        allExtraAttributes.putAll(extraAttributes);
        return createDescriptor(parent, ModuleRevisionId.newInstance(dependencyParts[0], dependencyParts[1], dependencyParts[2], allExtraAttributes),
                force, transitive, changing, confs, excludeRules, Dependency.DEFAULT_CONFIGURATION);
    }

    public DependencyDescriptor createDescriptor(ModuleDescriptor parent, ModuleRevisionId moduleRevisionId,
                                                 boolean force, boolean transitive, boolean changing,
                                                 Set<String> confs, List<ExcludeRule> excludeRules, String dependencyConfiguration) {
        DefaultDependencyDescriptor dd = new DefaultDependencyDescriptor(parent, moduleRevisionId, force, changing, transitive);
        for (String conf : confs) {
            dd.addDependencyConfiguration(conf, dependencyConfiguration);
            for (ExcludeRule excludeRule : excludeRules) {
                 dd.addExcludeRule(conf, excludeRule);
            }
        }
        return dd;
    }

}
