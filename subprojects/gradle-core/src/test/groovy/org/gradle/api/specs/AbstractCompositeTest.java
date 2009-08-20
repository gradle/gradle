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
package org.gradle.api.specs;

import org.gradle.util.WrapUtil;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Hans Dockter
 */
abstract public class AbstractCompositeTest {
    private Spec spec1;
    private Spec spec2;

    public abstract CompositeSpec createCompositeSpec(Spec... specs);

    @Before
    public void setUp() {
        spec1 = new Spec<Object>() {
            public boolean isSatisfiedBy(Object o) {
                return false;
            }
        };
        spec2 = new Spec<Object>() {
            public boolean isSatisfiedBy(Object o) {
                return false;
            }
        };
    }

    @Test
    public void init() {
        CompositeSpec compositeSpec = createCompositeSpec(spec1, spec2);
        assertEquals(WrapUtil.toList(spec1, spec2), compositeSpec.getSpecs());
    }

    protected Spec[] createAtomicElements(boolean... satisfies) {
        List<Spec> result = new ArrayList<Spec>();
        for (final boolean satisfy : satisfies) {
            result.add(new Spec<Object>() {
                public boolean isSatisfiedBy(Object o) {
                    return satisfy;
                }
            });
        }
        return result.toArray(new Spec[result.size()]);
    }

    @Test
    public void equality() {
        assertTrue(createCompositeSpec(spec1).equals(createCompositeSpec(spec1)));
        assertFalse(createCompositeSpec(spec1).equals(createCompositeSpec(spec2)));
    }
}
