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

import org.gradle.util.internal.CollectionUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertFalse;

public abstract class AbstractCompositeSpecTest {
    private Spec spec1;
    private Spec spec2;

    public abstract org.gradle.api.specs.CompositeSpec<Object> createCompositeSpec(Spec<Object>... specs);

    public abstract org.gradle.api.specs.CompositeSpec<Object> createOtherCompositeSpec(Spec<Object>... specs);

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
        Assert.assertEquals(CollectionUtils.flattenCollections(spec1, spec2), compositeSpec.getSpecs());
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
        return result.toArray(new Spec[0]);
    }

    @Test
    public void equality() {
        assert createCompositeSpec(spec1).equals(createCompositeSpec(spec1));
        assertFalse(createCompositeSpec(spec1).equals(createCompositeSpec(spec2)));

        assert createCompositeSpec(spec1, spec2).equals(createCompositeSpec(spec1, spec2));
        assert createCompositeSpec(spec2, spec1).equals(createCompositeSpec(spec2, spec1));
        assertFalse(createCompositeSpec(spec1, spec2).equals(createCompositeSpec(spec2, spec1)));

        assertFalse(createCompositeSpec(spec1).equals(createOtherCompositeSpec(spec1)));
        assertFalse(createCompositeSpec(spec2).equals(createOtherCompositeSpec(spec2)));
        assertFalse(createCompositeSpec(spec1, spec2).equals(createOtherCompositeSpec(spec1, spec2)));
    }

    @Test
    public void testHashCode() {
        assert createCompositeSpec(spec1).hashCode() == createCompositeSpec(spec1).hashCode();
        assert createCompositeSpec(spec1).hashCode() != createCompositeSpec(spec2).hashCode();

        assert createCompositeSpec(spec1, spec2).hashCode() == createCompositeSpec(spec1, spec2).hashCode();
        assert createCompositeSpec(spec2, spec1).hashCode() == createCompositeSpec(spec2, spec1).hashCode();
        assert createCompositeSpec(spec1, spec2).hashCode() != createCompositeSpec(spec2, spec1).hashCode();

        assert createCompositeSpec(spec1, spec2).hashCode() != createOtherCompositeSpec(spec1, spec2).hashCode();
        assert createCompositeSpec(spec2, spec1).hashCode() != createOtherCompositeSpec(spec2, spec1).hashCode();
        assert createCompositeSpec(spec1, spec2).hashCode() != createOtherCompositeSpec(spec2, spec1).hashCode();
    }

}
