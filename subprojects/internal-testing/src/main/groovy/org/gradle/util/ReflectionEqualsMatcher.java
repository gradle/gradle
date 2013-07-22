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
package org.gradle.util;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;

public class ReflectionEqualsMatcher<T> extends BaseMatcher<T> {
    private T toMatch;

    ReflectionEqualsMatcher(T toMatch) {
        this.toMatch = toMatch;
    }

    public boolean matches(Object o) {
        return EqualsBuilder.reflectionEquals(toMatch, o);
    }

    public void describeTo(Description description) {
        description.appendText("an Object with the same fields as " + toMatch);
    }
}
