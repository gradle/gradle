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

import org.gradle.internal.SystemProperties;
import org.hamcrest.BaseMatcher;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.jspecify.annotations.NullMarked;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.StringReader;
import java.util.regex.Pattern;

import static org.hamcrest.CoreMatchers.equalTo;

public class Matchers {

    public static <T extends CharSequence> Matcher<T> matchesRegexp(final String pattern) {
        return new BaseMatcher<T>() {
            @Override
            public boolean matches(Object o) {
                return Pattern.compile(pattern, Pattern.DOTALL).matcher((CharSequence) o).matches();
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("a CharSequence that matches regexp ").appendValue(pattern);
            }
        };
    }

    public static <T extends CharSequence> Matcher<T> matchesRegexp(final Pattern pattern) {
        return new BaseMatcher<T>() {
            @Override
            public boolean matches(Object o) {
                return pattern.matcher((CharSequence) o).matches();
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("a CharSequence that matches regexp ").appendValue(pattern);
            }
        };
    }

    public static <T extends CharSequence> Matcher<T> containsText(final String pattern) {
        return new BaseMatcher<T>() {
            @Override
            public boolean matches(Object o) {
                return ((String) o).contains(pattern);
            }
            @Override
            public void describeTo(Description description) {
                description.appendText("a CharSequence that contains text ").appendValue(pattern);
            }
        };
    }

    public static <T> Matcher<T> strictlyEqual(final T other) {
        return new BaseMatcher<T>() {
            @Override
            public boolean matches(Object o) {
                return strictlyEquals(o, other);
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("an Object that strictly equals ").appendValue(other);
            }
        };
    }

    @SuppressWarnings("EqualsWithItself")
    public static boolean strictlyEquals(Object a, Object b) {
        if (!a.equals(b)) {
            return false;
        }
        if (!b.equals(a)) {
            return false;
        }
        if (!a.equals(a)) {
            return false;
        }
        if (b.equals(new Object())) {
            return false;
        }
        return a.hashCode() == b.hashCode();
    }

    /**
     * Returns a matcher that matches if the examined object compares equal to it in both directions.
     *
     * @param other the object to compare against
     * @return a matcher that matches if the examined object compares equal to it in both directions
     * @param <T> the type of the objects being compared
     */
    public static <T extends Comparable<T>> Matcher<T> strictlyComparesEqual(final T other) {
        return new ComparableMatcher<T>(other) {
            @Override
            protected String getComparisonPhrase() {
                return "compares equal to";
            }

            @Override
            public boolean matchesSafely(T t) {
                return t.compareTo(other) == 0 && other.compareTo(t) == 0;
            }
        };
    }

    /**
     * Returns a matcher that matches if the examined object compares less than the given object in both directions.
     *
     * @param other the object to compare against
     * @return a matcher that matches if the examined object compares less than the given object in both directions
     * @param <T> the type of the objects being compared
     */
    public static <T extends Comparable<T>> Matcher<T> strictlyLessThan(final T other) {
        return new ComparableMatcher<T>(other) {
            @Override
            protected String getComparisonPhrase() {
                return "less than";
            }

            @Override
            public boolean matchesSafely(T t) {
                return t.compareTo(other) < 0 && other.compareTo(t) > 0;
            }
        };
    }

    /**
     * Returns a matcher that matches if the examined object compares greater than the given object in both directions.
     *
     * @param other the object to compare against
     * @return a matcher that matches if the examined object compares greater than the given object in both directions
     * @param <T> the type of the objects being compared
     */
    public static <T extends Comparable<T>> Matcher<T> strictlyGreaterThan(final T other) {
        return new ComparableMatcher<T>(other) {
            @Override
            protected String getComparisonPhrase() {
                return "greater than";
            }

            @Override
            public boolean matchesSafely(T t) {
                return t.compareTo(other) > 0 && other.compareTo(t) < 0;
            }
        };
    }

    @NullMarked
    private static abstract class ComparableMatcher<T extends Comparable<T>> extends TypeSafeMatcher<T> {
        protected final T other;

        ComparableMatcher(T other) {
            this.other = other;
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("an Object that is strictly " + getComparisonPhrase() + " ").appendValue(other);
        }

        protected abstract String getComparisonPhrase();
    }

    public static Matcher<String> containsLine(final Matcher<? super String> matcher) {
        return new BaseMatcher<String>() {
            @Override
            public boolean matches(Object o) {
                String str = (String) o;
                return containsLine(str, matcher);
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("a String that contains line that is ").appendDescriptionOf(matcher);
            }
        };
    }

    public static boolean containsLine(String action, String expected) {
        return containsLine(action, equalTo(expected));
    }

    public static boolean containsLine(String actual, Matcher<? super String> matcher) {
        BufferedReader reader = new BufferedReader(new StringReader(actual));
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

    public static Matcher<Iterable<?>> isEmpty() {
        return new BaseMatcher<Iterable<?>>() {
            @Override
            public boolean matches(Object o) {
                Iterable<?> iterable = (Iterable<?>) o;
                return iterable != null && !iterable.iterator().hasNext();
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("an empty Iterable");
            }
        };
    }

    public static Matcher<Object> isSerializable() {
        return new BaseMatcher<Object>() {
            @Override
            public boolean matches(Object o) {
                try {
                    new ObjectOutputStream(new ByteArrayOutputStream()).writeObject(o);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                return true;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("is serializable");
            }
        };
    }

    public static Matcher<Throwable> hasMessage(final Matcher<String> matcher) {
        return new BaseMatcher<Throwable>() {
            @Override
            public boolean matches(Object o) {
                Throwable t = (Throwable) o;
                return matcher.matches(t.getMessage());
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("an exception with message that is ").appendDescriptionOf(matcher);
            }
        };
    }

    public static Matcher<String> normalizedLineSeparators(final Matcher<? super String> matcher) {
        return new BaseMatcher<String>() {
            @Override
            public boolean matches(Object o) {
                String string = (String) o;
                return matcher.matches(string.replace(SystemProperties.getInstance().getLineSeparator(), "\n"));
            }

            @Override
            public void describeTo(Description description) {
                matcher.describeTo(description);
                description.appendText(" (normalize line separators)");
            }
        };
    }

    public static Matcher<String> containsNormalizedString(String substring) {
        return normalizedLineSeparators(CoreMatchers.containsString(substring));
    }
}
