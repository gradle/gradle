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

import org.apache.ivy.core.module.descriptor.ExcludeRule;
import org.apache.ivy.plugins.matcher.PatternMatcher;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.IvyArtifactName;

/**
 * A ModuleResolutionFilter that accepts any module/artifact that doesn't match the exclude rule.
 */
class IvyPatternMatcherExcludeRuleSpec extends AbstractModuleExcludeRuleFilter {
    private final ModuleIdentifier moduleId;
    private final IvyArtifactName ivyArtifactName;
    private final PatternMatcher matcher;
    private final boolean isArtifactExclude;

    IvyPatternMatcherExcludeRuleSpec(ExcludeRule rule) {
        this.moduleId = DefaultModuleIdentifier.newId(rule.getId().getModuleId().getOrganisation(), rule.getId().getModuleId().getName());
        this.ivyArtifactName = new DefaultIvyArtifactName(rule.getId().getName(), rule.getId().getType(), rule.getId().getExt());
        this.matcher = rule.getMatcher();
        isArtifactExclude = !isWildcard(ivyArtifactName.getName()) || !isWildcard(ivyArtifactName.getType()) || !isWildcard(ivyArtifactName.getExtension());
    }

    @Override
    public String toString() {
        return String.format("{exclude-rule %s:%s with matcher %s}", moduleId, ivyArtifactName, matcher.getName());
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (o == null || o.getClass() != getClass()) {
            return false;
        }
        IvyPatternMatcherExcludeRuleSpec other = (IvyPatternMatcherExcludeRuleSpec) o;
        return doAcceptsSameModulesAs(other);
    }

    @Override
    public int hashCode() {
        return moduleId.hashCode() ^ ivyArtifactName.hashCode();
    }

    @Override
    protected boolean doAcceptsSameModulesAs(AbstractModuleExcludeRuleFilter other) {
        IvyPatternMatcherExcludeRuleSpec otherSpec = (IvyPatternMatcherExcludeRuleSpec) other;
        return moduleId.equals(otherSpec.moduleId)
                && ivyArtifactName.equals(otherSpec.ivyArtifactName)
                && matcher.getName().equals(otherSpec.matcher.getName());
    }

    @Override
    protected boolean acceptsAllModules() {
        return isArtifactExclude;
    }

    public boolean acceptModule(ModuleIdentifier module) {
        return isArtifactExclude || !(matches(moduleId.getGroup(), module.getGroup()) && matches(moduleId.getName(), module.getName()));
    }

    public boolean acceptArtifact(ModuleIdentifier module, IvyArtifactName artifact) {
        if (isArtifactExclude) {
            return !(matches(moduleId.getGroup(), module.getGroup())
                    && matches(moduleId.getName(), module.getName())
                    && matches(ivyArtifactName.getName(), artifact.getName())
                    && matches(ivyArtifactName.getExtension(), artifact.getExtension())
                    && matches(ivyArtifactName.getType(), artifact.getType()));
        }
        return true;
    }

    public boolean acceptsAllArtifacts() {
        return !isArtifactExclude;
    }

    private boolean matches(String expression, String input) {
        return matcher.getMatcher(expression).matches(input);
    }
}
