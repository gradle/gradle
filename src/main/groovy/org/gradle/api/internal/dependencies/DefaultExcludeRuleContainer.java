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
import org.gradle.api.InvalidUserDataException;
import org.gradle.util.GUtil;

import java.util.*;

/**
 * @author Hans Dockter
 */
public class DefaultExcludeRuleContainer implements ExcludeRuleContainer {
    public static final ExcludeRuleContainer NO_RULES = new DefaultExcludeRuleContainer();

    private List<ExcludeRule> nativeRules = new ArrayList<ExcludeRule>();
    private Map<Map<String, String>, List<String>> addedRules = new HashMap<Map<String, String>, List<String>>();

    public void add(Map<String, String> args) {
        add(args, Collections.EMPTY_LIST);
    }

    public List<ExcludeRule> getNativeRules() {
        return nativeRules;
    }

    public void setNativeRules(List<ExcludeRule> excludeRules) {
        this.nativeRules = excludeRules;
    }

    public void add(Map<String, String> args, List<String> confs) {
        throwExceptionIfNull(confs, "The conf list");
        checkConfsForNull(confs);
        addedRules.put(args, confs);
    }

    private void checkConfsForNull(List<String> confs) {
        for (String conf : confs) {
            throwExceptionIfNull(conf, "A configuration element");
        }
    }

    public List<ExcludeRule> getRules(List<String> masterConfs) {
        List<ExcludeRule> allRules = new ArrayList<ExcludeRule>(nativeRules);
        for (Map<String, String> ruleProperties : addedRules.keySet()) {
            DefaultExcludeRule excludeRule = createExcludeRules(ruleProperties);
            addConfsIfNecessary(masterConfs, addedRules.get(ruleProperties), excludeRule);
            allRules.add(excludeRule);
        }
        return allRules;
    }

    private DefaultExcludeRule createExcludeRules(Map<String, String> ruleProperties) {
        String org = GUtil.elvis(ruleProperties.get("org"), PatternMatcher.ANY_EXPRESSION);
        String module = GUtil.elvis(ruleProperties.get("module"), PatternMatcher.ANY_EXPRESSION);
        DefaultExcludeRule excludeRule = new DefaultExcludeRule(new ArtifactId(
                new ModuleId(org, module), PatternMatcher.ANY_EXPRESSION,
                PatternMatcher.ANY_EXPRESSION,
                PatternMatcher.ANY_EXPRESSION),
                ExactPatternMatcher.INSTANCE, null);
        return excludeRule;
    }

    private void addConfsIfNecessary(List<String> masterConfs, List<String> ruleConfs, DefaultExcludeRule excludeRule) {
        List<String> confs = ruleConfs.isEmpty() ? masterConfs : ruleConfs;
        for (String masterConf : confs) {
            excludeRule.addConfiguration(masterConf);
        }
    }

    private void throwExceptionIfNull(Object property, String text) {
        if (property == null) {
            throw new InvalidUserDataException(text + " must not be null.");
        }
    }
}
