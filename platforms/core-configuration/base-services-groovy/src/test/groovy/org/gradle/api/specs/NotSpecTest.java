/*
 * Copyright 2009 the original author or authors.
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

import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

public class NotSpecTest {
    @Test
    public void testIsSatisfiedBy() {
        assertThat(new org.gradle.api.specs.NotSpec(createFilterSpec()).isSatisfiedBy(true), equalTo(false));
        assertThat(new org.gradle.api.specs.NotSpec(createFilterSpec()).isSatisfiedBy(false), equalTo(true));
    }

    private Spec<Boolean> createFilterSpec() {
        return new Spec<Boolean>() {
            public boolean isSatisfiedBy(Boolean element) {
                return element;
            }
        };
    }
}
