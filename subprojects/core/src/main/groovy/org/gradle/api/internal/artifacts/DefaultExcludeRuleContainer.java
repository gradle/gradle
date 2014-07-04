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
import org.gradle.internal.typeconversion.NotationParser;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class DefaultExcludeRuleContainer implements ExcludeRuleContainer {
    private Set<ExcludeRule> addedRules = new LinkedHashSet<ExcludeRule>();
    private final NotationParser<Object, ExcludeRule> notationParser = new ExcludeRuleNotationParser();

    public DefaultExcludeRuleContainer() {}

    public DefaultExcludeRuleContainer(Set<ExcludeRule> addedRules) {
        this.addedRules = new HashSet<ExcludeRule>(addedRules);
    }

    public void add(Map<String, String> args) {
        addedRules.add(notationParser.parseNotation(args));
    }

    public Set<ExcludeRule> getRules() {
        return addedRules;
    }
}
