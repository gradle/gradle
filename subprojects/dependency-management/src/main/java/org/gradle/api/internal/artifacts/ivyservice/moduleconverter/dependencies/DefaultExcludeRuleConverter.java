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

import org.apache.ivy.core.module.id.ArtifactId;
import org.apache.ivy.plugins.matcher.ExactPatternMatcher;
import org.apache.ivy.plugins.matcher.PatternMatcher;
import org.gradle.api.artifacts.ExcludeRule;
import org.gradle.api.internal.artifacts.ivyservice.IvyUtil;
import org.gradle.internal.component.external.descriptor.DefaultExcludeRule;
import org.gradle.util.GUtil;

public class DefaultExcludeRuleConverter implements ExcludeRuleConverter {
    public DefaultExcludeRule createExcludeRule(String configurationName, ExcludeRule excludeRule) {
        String org = GUtil.elvis(excludeRule.getGroup(), PatternMatcher.ANY_EXPRESSION);
        String module = GUtil.elvis(excludeRule.getModule(), PatternMatcher.ANY_EXPRESSION);
        ArtifactId artifactId = IvyUtil.createArtifactId(org, module, PatternMatcher.ANY_EXPRESSION, PatternMatcher.ANY_EXPRESSION, PatternMatcher.ANY_EXPRESSION);
        return new DefaultExcludeRule(artifactId, new String[] {configurationName}, ExactPatternMatcher.INSTANCE);
    }
}
