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

import org.gradle.util.TestUtil;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AndSpecTest extends AbstractCompositeSpecTest {
    public org.gradle.api.specs.CompositeSpec<Object> createCompositeSpec(Spec<Object>... specs) {
        return new AndSpec<Object>(specs);
    }

    public org.gradle.api.specs.CompositeSpec<Object> createOtherCompositeSpec(Spec<Object>... specs) {
        return new OrSpec<Object>(specs);
    }

    @Test
    public void isSatisfiedWhenNoSpecs() {
        assertTrue(new AndSpec<Object>().isSatisfiedBy(new Object()));
    }

    @Test
    public void isSatisfiedByWithAllTrue() {
        assertTrue(new AndSpec<Object>(createAtomicElements(true, true, true)).isSatisfiedBy(new Object()));
    }

    @Test
    public void isSatisfiedByWithOneFalse() {
        assertFalse(new AndSpec<Object>(createAtomicElements(true, false, true)).isSatisfiedBy(new Object()));
    }

    @Test
    public void canAddSpecs() {
        AndSpec<Object> spec = new AndSpec<Object>(createAtomicElements(true));
        spec = spec.and(createAtomicElements(false));
        assertFalse(spec.isSatisfiedBy(new Object()));
    }

    @Test
    public void canAddClosureAsASpec() {
        AndSpec<Object> spec = new AndSpec<Object>(createAtomicElements(true));
        spec = spec.and(TestUtil.toClosure("{ false }"));
        assertFalse(spec.isSatisfiedBy(new Object()));
    }
}
