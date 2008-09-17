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

import org.apache.ivy.core.module.descriptor.DefaultExcludeRule;
import org.apache.ivy.core.module.descriptor.ExcludeRule;
import org.apache.ivy.core.module.id.ArtifactId;
import org.apache.ivy.core.module.id.ModuleId;
import org.apache.ivy.plugins.matcher.ExactPatternMatcher;
import org.apache.ivy.plugins.matcher.PatternMatcher;
import org.gradle.api.dependencies.ExcludeRuleContainer;
import org.gradle.util.GUtil;
import org.gradle.util.WrapUtil;

import java.util.*;

/**
 * @author Hans Dockter
 */
public class DefaultExcludeRuleContainer implements ExcludeRuleContainer {
    private List<ExcludeRule> excludeRules = new ArrayList<ExcludeRule>();

    public void add(Map<String, String> args) {
        add(args, Collections.EMPTY_SET);
    }

    public List<ExcludeRule> getRules() {
        return excludeRules;
    }

    public void setRules(List<ExcludeRule> excludeRules) {
        this.excludeRules = excludeRules;
    }

    public void add(Map<String, String> args, Set<String> confs) {
        String org = GUtil.elvis(args.get("org"), PatternMatcher.ANY_EXPRESSION);
        String module = GUtil.elvis(args.get("module"), PatternMatcher.ANY_EXPRESSION);
        DefaultExcludeRule excludeRule = new DefaultExcludeRule(new ArtifactId(
                new ModuleId(org, module), PatternMatcher.ANY_EXPRESSION,
                PatternMatcher.ANY_EXPRESSION,
                PatternMatcher.ANY_EXPRESSION),
                ExactPatternMatcher.INSTANCE, null);
        excludeRules.add(excludeRule);
        for (String conf : confs) {
            excludeRule.addConfiguration(conf);   
        }
    }
}
