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
package org.gradle.api.dependencies;

import org.gradle.api.internal.project.DefaultProject;
import org.gradle.util.WrapUtil;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

import java.util.Set;

/**
 * @author Hans Dockter
 */
abstract public class AbstractDependencyTest {
    protected static final String TEST_CONF = "conf";
    protected static final Set<String> TEST_CONF_SET = WrapUtil.toSet(TEST_CONF);

    protected static final DefaultProject TEST_PROJECT = new DefaultProject();

    protected abstract AbstractDependency getDependency();
    protected abstract Object getUserDescription();

    @Test
    public void testGenericInit() {
        assertEquals(TEST_CONF_SET, getDependency().getConfs());
        assertEquals(getUserDescription(), getDependency().getUserDependencyDescription());
    }

}
