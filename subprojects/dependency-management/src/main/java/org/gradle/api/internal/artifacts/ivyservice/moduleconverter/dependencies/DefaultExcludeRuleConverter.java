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
package org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies;

import org.gradle.api.artifacts.ExcludeRule;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.PatternMatchers;
import org.gradle.internal.component.external.descriptor.DefaultExclude;
import org.gradle.util.GUtil;

public class DefaultExcludeRuleConverter implements ExcludeRuleConverter {
    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;

    public DefaultExcludeRuleConverter(ImmutableModuleIdentifierFactory moduleIdentifierFactory) {
        this.moduleIdentifierFactory = moduleIdentifierFactory;
    }

    public DefaultExclude convertExcludeRule(String configurationName, ExcludeRule excludeRule) {
        String org = GUtil.elvis(excludeRule.getGroup(), PatternMatchers.ANY_EXPRESSION);
        String module = GUtil.elvis(excludeRule.getModule(), PatternMatchers.ANY_EXPRESSION);
        String[] configurationNames = GUtil.isTrue(configurationName) ? new String[]{configurationName} : new String[0];
        return new DefaultExclude(moduleIdentifierFactory.module(org, module), configurationNames, PatternMatchers.EXACT);
    }
}
