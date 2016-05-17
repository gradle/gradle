/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes;

import org.apache.ivy.plugins.matcher.PatternMatcher;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.internal.component.model.Exclude;
import org.gradle.internal.component.model.IvyArtifactName;

/**
 * A ModuleResolutionFilter that excludes any module/artifact that matches the exclude rule, using an Ivy pattern matcher.
 */
class IvyPatternMatcherExcludeRuleSpec extends AbstractModuleExclusion {
    private final ModuleIdentifier moduleId;
    private final IvyArtifactName ivyArtifactName;
    private final PatternMatcher matcher;
    private final boolean isArtifactExclude;

    IvyPatternMatcherExcludeRuleSpec(Exclude rule) {
        this.moduleId = rule.getModuleId();
        this.ivyArtifactName = rule.getArtifact();
        this.matcher = PatternMatchers.getInstance().getMatcher(rule.getMatcher());
        isArtifactExclude = !isWildcard(ivyArtifactName.getName()) || !isWildcard(ivyArtifactName.getType()) || !isWildcard(ivyArtifactName.getExtension());
    }

    @Override
    public String toString() {
        return "{exclude-rule " + moduleId + ":" + ivyArtifactName + " with matcher " + matcher.getName() + "}";
    }

    @Override
    protected boolean doEquals(Object o) {
        IvyPatternMatcherExcludeRuleSpec other = (IvyPatternMatcherExcludeRuleSpec) o;
        return doExcludesSameModulesAs(other);
    }

    @Override
    protected int doHashCode() {
        return moduleId.hashCode() ^ ivyArtifactName.hashCode();
    }

    @Override
    protected boolean doExcludesSameModulesAs(AbstractModuleExclusion other) {
        IvyPatternMatcherExcludeRuleSpec otherSpec = (IvyPatternMatcherExcludeRuleSpec) other;
        return moduleId.equals(otherSpec.moduleId)
            && ivyArtifactName.equals(otherSpec.ivyArtifactName)
            && matcher.getName().equals(otherSpec.matcher.getName());
    }

    @Override
    protected boolean excludesNoModules() {
        return isArtifactExclude;
    }

    public boolean excludeModule(ModuleIdentifier module) {
        if (isArtifactExclude) {
            return false;
        }
        return matches(moduleId.getGroup(), module.getGroup()) && matches(moduleId.getName(), module.getName());
    }

    public boolean excludeArtifact(ModuleIdentifier module, IvyArtifactName artifact) {
        if (!isArtifactExclude) {
            return false;
        }
        return matches(moduleId.getGroup(), module.getGroup())
            && matches(moduleId.getName(), module.getName())
            && matches(ivyArtifactName.getName(), artifact.getName())
            && matches(ivyArtifactName.getExtension(), artifact.getExtension())
            && matches(ivyArtifactName.getType(), artifact.getType());
    }

    public boolean mayExcludeArtifacts() {
        return isArtifactExclude;
    }

    private boolean matches(String expression, String input) {
        return matcher.getMatcher(expression).matches(input);
    }
}
