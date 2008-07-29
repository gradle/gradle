/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.dependencies;

import groovy.lang.GString;
import org.apache.ivy.core.module.descriptor.DefaultExcludeRule;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.id.ArtifactId;
import org.apache.ivy.core.module.id.ModuleId;
import org.apache.ivy.plugins.matcher.ExactPatternMatcher;
import org.apache.ivy.plugins.matcher.PatternMatcher;
import org.gradle.api.Project;
import org.gradle.api.internal.dependencies.DependenciesUtil;
import org.gradle.api.internal.dependencies.DependencyDescriptorFactory;
import org.gradle.util.GUtil;
import org.gradle.util.WrapUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Hans Dockter
 */
public class ModuleDependency extends AbstractDependency {
    private boolean force = false;

    private List excludeRules = new ArrayList();

    private DependencyDescriptorFactory dependencyDescriptorFactory = new DependencyDescriptorFactory();

    public ModuleDependency(Object userDependencyDescription) {
        super(null, userDependencyDescription, null);
    }

    public ModuleDependency(Set confs, Object userDependencyDescription, Project project) {
        super(confs, userDependencyDescription, project);
    }

    public boolean isValidDescription(Object userDependencyDescription) {
        if (DependenciesUtil.hasExtension(userDependencyDescription.toString())) {
            return false;
        }
        int elementCount = (userDependencyDescription.toString()).split(":").length;
        return (elementCount == 3 || elementCount == 4);
    }

    public Class[] userDepencencyDescriptionType() {
        return WrapUtil.toArray(String.class, GString.class);
    }

    public DependencyDescriptor createDepencencyDescriptor() {
        return dependencyDescriptorFactory.createDescriptor(getUserDependencyDescription().toString(), force, true, false, getConfs(),
                excludeRules);
    }

    public ModuleDependency exclude(Map<String, String> args) {
        String org = GUtil.elvis(args.get("org"), PatternMatcher.ANY_EXPRESSION);
        String module = GUtil.elvis(args.get("module"), PatternMatcher.ANY_EXPRESSION);
        excludeRules.add(new DefaultExcludeRule(new ArtifactId(
                new ModuleId(org, module), PatternMatcher.ANY_EXPRESSION,
                PatternMatcher.ANY_EXPRESSION,
                PatternMatcher.ANY_EXPRESSION),
                ExactPatternMatcher.INSTANCE, null));
        return this;
    }

    public ModuleDependency force(boolean force) {
        this.force = force;
        return this;
    }

    public boolean isForce() {
        return force;
    }

    public void setForce(boolean force) {
        this.force = force;
    }

    public List getExcludeRules() {
        return excludeRules;
    }

    public void setExcludeRules(List excludeRules) {
        this.excludeRules = excludeRules;
    }

    public DependencyDescriptorFactory getDependencyDescriptorFactory() {
        return dependencyDescriptorFactory;
    }

    public void setDependencyDescriptorFactory(DependencyDescriptorFactory dependencyDescriptorFactory) {
        this.dependencyDescriptorFactory = dependencyDescriptorFactory;
    }
}
