/*
 * Copyright 2010 the original author or authors.
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
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.gradle.util.Matchers.strictlyEqual;
import static org.junit.Assert.*;

/**
 * @author Hans Dockter
 */
abstract public class AbstractCompositeSpecTest {
    private Spec spec1;
    private Spec spec2;

    public abstract org.gradle.api.specs.CompositeSpec<Object> createCompositeSpec(Spec<Object>... specs);

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
        org.gradle.api.specs.CompositeSpec<Object> compositeSpec = createCompositeSpec(spec1, spec2);
        assertEquals(WrapUtil.toList(spec1, spec2), compositeSpec.getSpecs());
    }

    protected Spec<Object>[] createAtomicElements(boolean... satisfies) {
        List<Spec<Object>> result = new ArrayList<Spec<Object>>();
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
        assertThat(createCompositeSpec(spec1), strictlyEqual(createCompositeSpec(spec1)));
        assertFalse(createCompositeSpec(spec1).equals(createCompositeSpec(spec2)));
    }
}
