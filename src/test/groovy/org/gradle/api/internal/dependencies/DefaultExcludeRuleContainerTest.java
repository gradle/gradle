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
import org.gradle.util.GUtil;
import org.gradle.util.WrapUtil;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.ArrayList;

/**
 * @author Hans Dockter
 */
public class DefaultExcludeRuleContainerTest {
    private DefaultExcludeRuleContainer excludeRuleContainer;
    private String expectedOrg;
    private String expectedModule;
    private String expectedOrg2;
    private String expectedModule2;

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
        assertEquals(0, excludeRuleContainer.getRules().size());
    }

    @Test
    public void testAdd() {
        excludeRuleContainer.add(GUtil.map("org", expectedOrg, "module", expectedModule));
        checkAdd();
    }

    @Test
    public void testAddWithConfigurations() {
        Set<String> confs = WrapUtil.toSet("conf1", "conf2");
        excludeRuleContainer.add(GUtil.map("org", expectedOrg, "module", expectedModule), confs);
        assertEquals(Arrays.asList(excludeRuleContainer.getRules().get(0).getConfigurations()), new ArrayList(confs));
        checkAdd();
    }

    private void checkAdd() {

        assertEquals(1, excludeRuleContainer.getRules().size());
        assertEquals(excludeRuleContainer.getRules().get(0).getAttribute(IvyPatternHelper.ORGANISATION_KEY),
                expectedOrg);
        assertEquals(excludeRuleContainer.getRules().get(0).getAttribute(IvyPatternHelper.MODULE_KEY),
                expectedModule);
        excludeRuleContainer.add(GUtil.map("org", expectedOrg2, "module", expectedModule2));
        assertEquals(2, excludeRuleContainer.getRules().size());
        assertEquals(excludeRuleContainer.getRules().get(1).getAttribute(IvyPatternHelper.ORGANISATION_KEY),
                expectedOrg2);
        assertEquals(excludeRuleContainer.getRules().get(1).getAttribute(IvyPatternHelper.MODULE_KEY),
                expectedModule2);
    }


}
