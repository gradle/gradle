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

package org.gradle.internal.component.external.descriptor;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.PatternMatchers;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.Exclude;
import org.gradle.internal.component.model.IvyArtifactName;

import java.util.Set;

public class DefaultExclude implements Exclude {
    private final ModuleIdentifier moduleId;
    private final IvyArtifactName artifact;
    private final Set<String> configurations;
    private final String patternMatcher;

    public DefaultExclude(String group, String module, String artifact, String type, String extension, String[] configurations, String patternMatcher) {
        this.moduleId = DefaultModuleIdentifier.newId(group, module);
        this.artifact = new DefaultIvyArtifactName(artifact, type, extension);
        this.configurations = ImmutableSet.copyOf(configurations);
        this.patternMatcher = patternMatcher;
    }

    public DefaultExclude(String group, String module, String[] configurations, String patternMatcher) {
        this.moduleId = DefaultModuleIdentifier.newId(group, module);
        this.artifact = new DefaultIvyArtifactName(PatternMatchers.ANY_EXPRESSION, PatternMatchers.ANY_EXPRESSION, PatternMatchers.ANY_EXPRESSION);
        this.configurations = ImmutableSet.copyOf(configurations);
        this.patternMatcher = patternMatcher;
    }

    public DefaultExclude(String group, String module) {
        this.moduleId = DefaultModuleIdentifier.newId(group, module);
        this.artifact = new DefaultIvyArtifactName(PatternMatchers.ANY_EXPRESSION, PatternMatchers.ANY_EXPRESSION, PatternMatchers.ANY_EXPRESSION);
        this.configurations = ImmutableSet.of();
        this.patternMatcher = PatternMatchers.EXACT;
    }

    @Override
    public String toString() {
        return "{exclude moduleId: " + moduleId + ", artifact: " + artifact + "}";
    }

    @Override
    public ModuleIdentifier getModuleId() {
        return moduleId;
    }

    @Override
    public IvyArtifactName getArtifact() {
        return artifact;
    }

    @Override
    public Set<String> getConfigurations() {
        return configurations;
    }

    @Override
    public String getMatcher() {
        return patternMatcher;
    }
}
