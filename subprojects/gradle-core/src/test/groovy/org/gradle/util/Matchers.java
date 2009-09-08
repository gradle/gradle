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

import java.util.Map;
import java.util.regex.Pattern;
import java.io.BufferedReader;
import java.io.StringReader;
import java.io.IOException;

public class Matchers {
    @Factory
    public static <T> Matcher<T> reflectionEquals(T equalsTo) {
        return new ReflectionEqualsMatcher<T>(equalsTo);
    }

    @Factory
    public static <T extends CharSequence> Matcher<T> matchesRegexp(final String pattern) {
        return new BaseMatcher<T>() {
            public boolean matches(Object o) {
                return Pattern.compile(pattern).matcher((CharSequence) o).matches();
            }

            public void describeTo(Description description) {
                description.appendText("matches regexp ").appendValue(pattern);
            }
        };
    }

    @Factory
    public static <T> Matcher<T> strictlyEqual(final T other) {
        return new BaseMatcher<T>() {
            public boolean matches(Object o) {
                if (!o.equals(other)) {
                    return false;
                }
                if (!other.equals(o)) {
                    return false;
                }
                if (!o.equals(o)) {
                    return false;
                }
                if (other.equals(null)) {
                    return false;
                }
                if (other.equals(new Object())) {
                    return false;
                }
                if (o.hashCode() != other.hashCode()) {
                    return false;
                }
                return true;
            }

            public void describeTo(Description description) {
                description.appendText("strictly equals ").appendValue(other);
            }
        };
    }

    @Factory
    public static Matcher<String> containsLine(final String line) {
        return new BaseMatcher<String>() {
            public boolean matches(Object o) {
                return containsLine(org.hamcrest.Matchers.equalTo(line)).matches(o);
            }

            public void describeTo(Description description) {
                description.appendText("contains line ").appendValue(line);
            }
        };
    }

    @Factory
    public static Matcher<String> containsLine(final Matcher<? super String> matcher) {
        return new BaseMatcher<String>() {
            public boolean matches(Object o) {
                String str = (String) o;
                BufferedReader reader = new BufferedReader(new StringReader(str));
                String line;
                try {
                    while ((line = reader.readLine()) != null) {
                        if (matcher.matches(line)) {
                            return true;
                        }
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                return false;
            }

            public void describeTo(Description description) {
                description.appendText("contains line that matches ").appendDescriptionOf(matcher);
            }
        };
    }

    @Factory
    public static Matcher<Iterable<?>> isEmpty() {
        return new BaseMatcher<Iterable<?>>() {
            public boolean matches(Object o) {
                Iterable<?> iterable = (Iterable<?>) o;
                return iterable != null && !iterable.iterator().hasNext();
            }

            public void describeTo(Description description) {
                description.appendText("is empty");
            }
        };
    }

    @Factory
    public static Matcher<Map<?, ?>> isEmptyMap() {
        return new BaseMatcher<Map<?, ?>>() {
            public boolean matches(Object o) {
                Map<?, ?> map = (Map<?, ?>) o;
                return map.isEmpty();
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

    public static Matcher<Throwable> hasMessage(final Matcher<String> matcher) {
        return new BaseMatcher<Throwable>() {
            public boolean matches(Object o) {
                Throwable t = (Throwable) o;
                return matcher.matches(t.getMessage());
            }

            public void describeTo(Description description) {
                description.appendText("exception messages ").appendDescriptionOf(matcher);
            }
        };
    }
}
