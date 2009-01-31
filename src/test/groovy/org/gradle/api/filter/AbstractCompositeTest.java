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
package org.gradle.api.filter;

import org.gradle.api.filter.CompositeSpec;
import org.gradle.api.dependencies.Dependency;
import org.gradle.util.WrapUtil;
import org.junit.Test;
import org.junit.Before;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import java.util.List;
import java.util.ArrayList;

/**
 * @author Hans Dockter
 */
abstract public class AbstractCompositeTest {
    private FilterSpec filterSpec1;
    private FilterSpec filterSpec2;

    public abstract CompositeSpec createCompositeSpec(FilterSpec... filterSpecs);

    @Before
    public void setUp() {
        filterSpec1 = new FilterSpec<Object>() {
            public boolean isSatisfiedBy(Object o) {
                return false;
            }
        };
        filterSpec2 = new FilterSpec<Object>() {
            public boolean isSatisfiedBy(Object o) {
                return false;
            }
        };
    }

    @Test
    public void init() {
        CompositeSpec compositeSpec = createCompositeSpec(filterSpec1, filterSpec2);
        assertEquals(WrapUtil.toList(filterSpec1, filterSpec2), compositeSpec.getSpecs());
    }

    protected FilterSpec[] createAtomicElements(boolean... satisfies) {
        List<FilterSpec> result = new ArrayList<FilterSpec>();
        for (final boolean satisfy : satisfies) {
            result.add(new FilterSpec<Object>() {
                public boolean isSatisfiedBy(Object o) {
                    return satisfy;
                }
            });
        }
        return result.toArray(new FilterSpec[result.size()]);
    }

    @Test
    public void equality() {
        assertTrue(createCompositeSpec(filterSpec1).equals(createCompositeSpec(filterSpec1)));
        assertFalse(createCompositeSpec(filterSpec1).equals(createCompositeSpec(filterSpec2)));
    }
}
