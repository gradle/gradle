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
package org.gradle.util;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Factory;
import org.hamcrest.Matcher;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;

import java.util.regex.Pattern;

public class Matchers {
    @Factory
    public static <T> Matcher<T> reflectionEquals(T equalsTo) {
        return new ReflectionEqualsMatcher<T>(equalsTo);
    }

    @Factory
    public static Matcher<String> containsLine(final String line) {
        return new BaseMatcher<String>() {
            public boolean matches(Object o) {
                return Pattern.compile(String.format("^%s$", Pattern.quote(line)), Pattern.MULTILINE).matcher(
                        o.toString()).find();
            }

            public void describeTo(Description description) {
                description.appendText("contains line ").appendValue(line);
            }
        };
    }

    @Factory
    public static Matcher<Iterable<?>> isEmpty() {
        return new BaseMatcher<Iterable<?>>() {
            public boolean matches(Object o) {
                Iterable<?> iterable = (Iterable<?>) o;
                return !iterable.iterator().hasNext();
            }

            public void describeTo(Description description) {
                description.appendText("is empty");
            }
        };
    }

    public static Matcher<Object[]> isEmptyArray() {
        return new BaseMatcher<Object[]>() {
            public boolean matches(Object o) {
                Object[] array = (Object[]) o;
                return array.length == 0;
            }

            public void describeTo(Description description) {
                description.appendText("is empty");
            }
        };
    }
}
