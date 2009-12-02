/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.artifacts.dependencies;

import org.gradle.api.artifacts.*;
import org.gradle.api.internal.artifacts.DefaultExcludeRuleContainer;
import org.gradle.util.ConfigureUtil;
import org.gradle.util.GUtil;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import groovy.lang.Closure;

public abstract class AbstractModuleDependency extends AbstractDependency implements ModuleDependency {
    private ExcludeRuleContainer excludeRuleContainer = new DefaultExcludeRuleContainer();
    private Set<DependencyArtifact> artifacts = new HashSet<DependencyArtifact>();
    private String configuration;
    private boolean transitive = true;

    protected AbstractModuleDependency(String configuration) {
        this.configuration = GUtil.elvis(configuration, Dependency.DEFAULT_CONFIGURATION);
    }

    public boolean isTransitive() {
        return transitive;
    }

    public ModuleDependency setTransitive(boolean transitive) {
        this.transitive = transitive;
        return this;
    }

    public String getConfiguration() {
        return configuration;
    }

    public ModuleDependency exclude(Map<String, String> excludeProperties) {
        excludeRuleContainer.add(excludeProperties);
        return this;
    }

    public Set<ExcludeRule> getExcludeRules() {
        return excludeRuleContainer.getRules();
    }

    private void setExcludeRuleContainer(ExcludeRuleContainer excludeRuleContainer) {
        this.excludeRuleContainer = excludeRuleContainer;
    }

    public Set<DependencyArtifact> getArtifacts() {
        return artifacts;
    }

    public void setArtifacts(Set<DependencyArtifact> artifacts) {
        this.artifacts = artifacts;
    }

    public AbstractModuleDependency addArtifact(DependencyArtifact artifact) {
        artifacts.add(artifact);
        return this;
    }

    public DependencyArtifact artifact(Closure configureClosure) {
        DependencyArtifact artifact = ConfigureUtil.configure(configureClosure, new DefaultDependencyArtifact());
        artifacts.add(artifact);
        return artifact;
    }

    protected void copyTo(AbstractModuleDependency target) {
        super.copyTo(target);
        target.setArtifacts(new HashSet<DependencyArtifact>(getArtifacts()));
        target.setExcludeRuleContainer(new DefaultExcludeRuleContainer(getExcludeRules()));
        target.setTransitive(isTransitive());
    }

    protected boolean isKeyEquals(ModuleDependency dependencyRhs) {
        if (getGroup() != null ? !getGroup().equals(dependencyRhs.getGroup()) : dependencyRhs.getGroup() != null) {
            return false;
        }
        if (!getName().equals(dependencyRhs.getName())) {
            return false;
        }
        if (!getConfiguration().equals(dependencyRhs.getConfiguration())) {
            return false;
        }
        if (getVersion() != null ? !getVersion().equals(dependencyRhs.getVersion())
                : dependencyRhs.getVersion() != null) {
            return false;
        }
        return true;
    }

    protected boolean isCommonContentEquals(ModuleDependency dependencyRhs) {
        if (!isKeyEquals(dependencyRhs)) {
            return false;
        }
        if (isTransitive() != dependencyRhs.isTransitive()) {
            return false;
        }
        if (getArtifacts() != null ? !getArtifacts().equals(dependencyRhs.getArtifacts())
                : dependencyRhs.getArtifacts() != null) {
            return false;
        }
        if (getExcludeRules() != null ? !getExcludeRules().equals(dependencyRhs.getExcludeRules())
                : dependencyRhs.getExcludeRules() != null) {
            return false;
        }
        return true;
    }
}
