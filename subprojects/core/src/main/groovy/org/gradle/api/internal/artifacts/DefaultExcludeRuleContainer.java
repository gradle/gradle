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
package org.gradle.api.internal.artifacts;

import org.gradle.api.artifacts.ExcludeRule;
import org.gradle.api.artifacts.ExcludeRuleContainer;
import org.gradle.util.DeprecationLogger;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author Hans Dockter
 */
public class DefaultExcludeRuleContainer implements ExcludeRuleContainer {
    private Set<ExcludeRule> addedRules = new LinkedHashSet<ExcludeRule>();

    public DefaultExcludeRuleContainer() {
    }

    public DefaultExcludeRuleContainer(Set<ExcludeRule> addedRules) {
        this.addedRules = new HashSet<ExcludeRule>(addedRules);
    }

    public void add(Map<String, String> args) {
        if(isValidExcludeRule(args)){
            addedRules.add(new DefaultExcludeRule(args));
        }else{
            DeprecationLogger.nagUserWith(String.format("Detected invalid Exclusion Rule %s. Exclude rule does not specify 'module' nor 'group' and will be ignored.", args));
        }
    }

    boolean isValidExcludeRule(Map<String, String> excludeMap) {
        return excludeMap.containsKey(ExcludeRule.GROUP_KEY) || excludeMap.containsKey(ExcludeRule.MODULE_KEY);
    }

    public Set<ExcludeRule> getRules() {
        return addedRules;
    }
}
