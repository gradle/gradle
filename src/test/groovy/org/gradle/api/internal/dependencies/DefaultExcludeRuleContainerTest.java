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

import org.apache.ivy.core.IvyPatternHelper;
import org.apache.ivy.core.module.descriptor.ExcludeRule;
import org.gradle.util.GUtil;
import org.gradle.util.WrapUtil;
import org.gradle.util.HelperUtil;
import org.gradle.api.InvalidUserDataException;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * @author Hans Dockter
 */
public class DefaultExcludeRuleContainerTest {
    private DefaultExcludeRuleContainer excludeRuleContainer;
    private String expectedOrg;
    private String expectedModule;
    private String expectedOrg2;
    private String expectedModule2;
    private static final List<String> TEST_CONF_SET = WrapUtil.toList("conf1", "conf2");

    @Before
    public void setUp() {
        excludeRuleContainer = new DefaultExcludeRuleContainer();
        expectedOrg = "org";
        expectedModule = "module";
        expectedOrg2 = "org2";
        expectedModule2 = "module2";
    }

    @Test
    public void testInit() {
        assertEquals(0, excludeRuleContainer.createRules(TEST_CONF_SET).size());
    }

    @Test
    public void testAdd() {
        excludeRuleContainer.add(GUtil.map("org", expectedOrg, "module", expectedModule));
        excludeRuleContainer.add(GUtil.map("org", expectedOrg2, "module", expectedModule2));
        List<ExcludeRule> excludeRules = excludeRuleContainer.createRules(TEST_CONF_SET);
        assertEquals(2, excludeRules.size());
        checkContainsRule(excludeRules, expectedOrg, expectedModule);
    }

    private void checkContainsRule(List<ExcludeRule> excludeRules, String org, String module) {
        boolean ruleFound = false;
        for (ExcludeRule excludeRule : excludeRules) {
            if (checkRule(excludeRule, expectedOrg, expectedModule)) {
                ruleFound = true;
            }
        }
        assertTrue(ruleFound);
    }

    @Test
    public void testAddWithConfigurations() {
        List<String> confs = WrapUtil.toList("conf3");
        excludeRuleContainer.add(GUtil.map("org", expectedOrg, "module", expectedModule), confs);
        List<ExcludeRule> excludeRules = excludeRuleContainer.createRules(TEST_CONF_SET);
        assertEquals(1, excludeRules.size());
        assertTrue(checkRule(excludeRuleContainer.createRules(TEST_CONF_SET).get(0), expectedOrg, expectedModule));
        assertEquals(confs, Arrays.asList(excludeRuleContainer.createRules(TEST_CONF_SET).get(0).getConfigurations()));
    }

    @Test
    public void testAddWithNativeRule() {
        excludeRuleContainer.add(GUtil.map("org", expectedOrg, "module", expectedModule));
        ExcludeRule nativeRule = HelperUtil.getTestExcludeRule();
        excludeRuleContainer.getNativeRules().add(nativeRule);
        List<ExcludeRule> excludeRules = excludeRuleContainer.createRules(TEST_CONF_SET);
        assertEquals(2, excludeRules.size());
        checkContainsRule(excludeRules, expectedOrg, expectedModule);
        assertTrue(excludeRules.contains(nativeRule));
    }

    private boolean checkRule(ExcludeRule excludeRule, String group, String name) {
        if (!excludeRule.getAttribute(IvyPatternHelper.ORGANISATION_KEY).equals(group)) {
            return false;
        };
        if (!excludeRule.getAttribute(IvyPatternHelper.MODULE_KEY).equals(name)) {
            return false;
        };
        return true;
    }

    @Test(expected= InvalidUserDataException.class)
    public void confListNull() {
        excludeRuleContainer.add(WrapUtil.<String, String>toMap("org", "jdjd"), null);
    }

    @Test(expected= InvalidUserDataException.class)
    public void confListElementNull() {
        excludeRuleContainer.add(WrapUtil.<String, String>toMap("org", "someValue"), WrapUtil.toList("conf", null));
    }


}
