/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.simple;

import com.google.common.base.Objects;
import org.apache.ivy.plugins.matcher.PatternMatcher;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.PatternMatchers;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.IvyPatternMatcherExcludeRuleSpec;
import org.gradle.internal.component.model.IvyArtifactName;

final class DefaultIvyPatternMatcherExcludeRuleSpec implements IvyPatternMatcherExcludeRuleSpec {
    private final ModuleIdentifier moduleId;
    private final IvyArtifactName ivyArtifactName;
    private final PatternMatcher matcher;
    private final boolean isArtifactExclude;
    private final int hashCode;

    public static ExcludeSpec of(ModuleIdentifier moduleId, IvyArtifactName artifact, String matcher) {
        return new DefaultIvyPatternMatcherExcludeRuleSpec(moduleId, artifact, matcher);
    }

    private DefaultIvyPatternMatcherExcludeRuleSpec(ModuleIdentifier moduleId, IvyArtifactName artifact, String matcher) {
        this.moduleId = moduleId;
        this.ivyArtifactName = artifact;
        this.matcher = PatternMatchers.getInstance().getMatcher(matcher);
        isArtifactExclude = ivyArtifactName != null;
        hashCode = Objects.hashCode(moduleId, ivyArtifactName, matcher, isArtifactExclude);
    }

    @Override
    public String toString() {
        return "{ \"exclude-rule\" : { \"moduleId\": \""  + moduleId + "\", \"artifact\" : \"" + ivyArtifactName + "\", \"matcher\": \"" + matcher.getName() + "\"} }";
    }

    @Override
    public boolean excludes(ModuleIdentifier module) {
        if (isArtifactExclude) {
            return false;
        }
        return matches(moduleId.getGroup(), module.getGroup()) && matches(moduleId.getName(), module.getName());
    }

    @Override
    public boolean excludesArtifact(ModuleIdentifier module, IvyArtifactName artifact) {
        if (!isArtifactExclude) {
            return false;
        }
        return matches(moduleId.getGroup(), module.getGroup())
            && matches(moduleId.getName(), module.getName())
            && matches(ivyArtifactName.getName(), artifact.getName())
            && matches(ivyArtifactName.getExtension(), artifact.getExtension())
            && matches(ivyArtifactName.getType(), artifact.getType());
    }

    @Override
    public boolean mayExcludeArtifacts() {
        return isArtifactExclude;
    }

    private boolean matches(String expression, String input) {
        return matcher.getMatcher(expression).matches(input);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DefaultIvyPatternMatcherExcludeRuleSpec that = (DefaultIvyPatternMatcherExcludeRuleSpec) o;
        return hashCode == that.hashCode &&
            isArtifactExclude == that.isArtifactExclude &&
            Objects.equal(moduleId, that.moduleId) &&
            Objects.equal(ivyArtifactName, that.ivyArtifactName) &&
            Objects.equal(matcher, that.matcher);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public IvyArtifactName getArtifact() {
        return ivyArtifactName;
    }

}
