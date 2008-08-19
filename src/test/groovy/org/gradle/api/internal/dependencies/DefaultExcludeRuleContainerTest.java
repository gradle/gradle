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
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Hans Dockter
 */
public class DefaultExcludeRuleContainerTest {
    private DefaultExcludeRuleContainer excludeRuleContainer;

    @Before
    public void setUp() {
        excludeRuleContainer = new DefaultExcludeRuleContainer();
    }

    @Test
    public void testInit() {
        assertEquals(0, excludeRuleContainer.getRules().size());
    }

    @Test
    public void testAdd() {
        String expectedOrg = "org";
        String expectedModule = "module";
        String expectedOrg2 = "org2";
        String expectedModule2 = "module2";
        excludeRuleContainer.add(GUtil.map("org", expectedOrg, "module", expectedModule));
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
