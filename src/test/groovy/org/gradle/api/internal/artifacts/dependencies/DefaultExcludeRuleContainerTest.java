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
package org.gradle.api.internal.artifacts.dependencies;

import org.gradle.api.artifacts.ExcludeRule;
import org.gradle.api.internal.artifacts.DefaultExcludeRule;
import org.gradle.api.internal.artifacts.DefaultExcludeRuleContainer;
import org.gradle.util.WrapUtil;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;
import org.junit.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author Hans Dockter
 */
public class DefaultExcludeRuleContainerTest {
    @Test
    public void testInit() {
        assertThat(new DefaultExcludeRuleContainer().getRules().size(), equalTo(0));
    }

    @Test
    public void testInitWithRules() {
        Set<ExcludeRule> sourceExcludeRules = new HashSet<ExcludeRule>();
        sourceExcludeRules.add(new DefaultExcludeRule(WrapUtil.toMap("key", "value")));
        DefaultExcludeRuleContainer defaultExcludeRuleContainer = new DefaultExcludeRuleContainer(sourceExcludeRules);
        assertThat(defaultExcludeRuleContainer.getRules(), equalTo(sourceExcludeRules));
        assertThat(defaultExcludeRuleContainer.getRules(), not(sameInstance(sourceExcludeRules)));
    }

    @Test
    public void testAdd() {
        DefaultExcludeRuleContainer excludeRuleContainer = new DefaultExcludeRuleContainer();
        Map excludeRuleArgs1 = WrapUtil.toMap("key1", "value1");
        Map excludeRuleArgs2 = WrapUtil.toMap("key2", "value2");
        excludeRuleContainer.add(excludeRuleArgs1);
        excludeRuleContainer.add(excludeRuleArgs2);
        assertThat(excludeRuleContainer.getRules().size(), equalTo(2));
        assertExcludeRuleContainerHasCorrectExcludeRules(excludeRuleContainer.getRules(), excludeRuleArgs1, excludeRuleArgs2);
    }

    private void assertExcludeRuleContainerHasCorrectExcludeRules(Set<ExcludeRule> excludeRules, Map... excludeRuleArgs) {
        Set<Map> foundRules = new HashSet<Map>();
        for (ExcludeRule excludeRule : excludeRules) {
            for (Map excludeRuleArg : excludeRuleArgs) {
                if (excludeRule.getExcludeArgs().equals(excludeRuleArg)) {
                    foundRules.add(excludeRuleArg);
                    continue;
                }
            }
        }
    }


}
