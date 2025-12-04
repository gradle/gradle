/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.report.generic;

import com.google.common.collect.AbstractIterator;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import org.gradle.util.Path;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A "selector" for test paths, which can match against multiple {@link Path}s.
 *
 * <p>
 * No relative paths are allowed, to simplify matching logic.
 * </p>
 *
 * <p>
 * In addition to the normal path syntax, the following special syntax is supported:
 * <ul>
 *     <li>Special characters ({@code :~_%}) can be escaped with a percent sign.
 *         Backslash was avoided to prevent extra escaping in regex segments.
 *     <li>A segment may start with {@code ~} to indicate a regex match. For example, {@code :com:~.*Test} will match
 *         {@code :com:MyTest} and {@code :com:YourTest}, but not {@code :com:TestCase}.
 *     <li>The last segment may be {@code _} to indicate that there are further "leaf" nodes below the specified path.
 *         It will not match those leaf nodes themselves, but this is necessary to allow interoperability between
 *         selecting tests from HTML and JUnit XML reports with the same code, as JUnit XML reports lack intermediate nodes.
 *         For example, {@code :com:example:_} will match {@code :com:example}, but not {@code :com:example:MyTest}.
 *         But it will not fail if an XML report does not contain a node for {@code :com:example}, but only {@code :com} and {@code :com:MyTest}.
 * </ul>
 * </p>
 */
public final class TestPathSelector {
    public static TestPathSelector from(String path) {
        ImmutableList<Segment> segments = ImmutableList.copyOf(segmentsFrom(path));
        boolean absolute = path.startsWith(":");
        if (!absolute) {
            throw new IllegalArgumentException("Only absolute paths are supported: " + path);
        }
        return new TestPathSelector(segments);
    }

    private static boolean isSpecialChar(char c) {
        return c == ':' || c == '~' || c == '_' || c == '%';
    }

    private static Iterator<Segment> segmentsFrom(String path) {
        if (path.isEmpty() || path.equals(":")) {
            return Collections.emptyIterator();
        }
        return new AbstractIterator<Segment>() {
            private int index = path.startsWith(":") ? 1 : 0;

            @Nullable
            @Override
            protected Segment computeNext() {
                if (index > path.length()) {
                    return endOfData();
                }
                if (index == path.length()) {
                    if (path.charAt(index - 1) == ':') {
                        index++;
                        return new Segment.Literal("");
                    } else {
                        return endOfData();
                    }
                }
                if (path.charAt(index) == '_') {
                    if (index + 1 != path.length()) {
                        throw new IllegalArgumentException("The '_' segment must be the last segment in the path: " + path);
                    }
                    index++;
                    return Segment.LeafMarker.INSTANCE;
                }
                Function<String, Segment> segmentConstructor;
                if (path.charAt(index) == '~') {
                    segmentConstructor = Segment.Regex::new;
                    index++;
                } else {
                    segmentConstructor = Segment.Literal::new;
                }
                StringBuilder segment = new StringBuilder();
                boolean escape = false;
                for (; index < path.length(); index++) {
                    char c = path.charAt(index);
                    if (escape) {
                        if (!isSpecialChar(c)) {
                            throw new IllegalArgumentException("Invalid escape sequence: %" + c);
                        }
                        segment.append(c);
                        escape = false;
                    } else if (c == '%') {
                        escape = true;
                    } else if (c == ':') {
                        index++;
                        break;
                    } else {
                        segment.append(c);
                    }
                }
                return segmentConstructor.apply(segment.toString());
            }
        };
    }

    private static String escapeSegment(String segment) {
        StringBuilder escaped = new StringBuilder(segment.length());
        for (int i = 0; i < segment.length(); i++) {
            char c = segment.charAt(i);
            if (isSpecialChar(c)) {
                escaped.append('%');
            }
            escaped.append(c);
        }
        return escaped.toString();
    }

    public interface Segment {
        class Literal implements Segment {
            private final String value;

            public Literal(String value) {
                this.value = value;
            }

            @Override
            public boolean matches(String segment) {
                return value.equals(segment);
            }

            @Override
            public String toString() {
                return escapeSegment(value);
            }
        }

        class Regex implements Segment {
            private final Pattern pattern;

            public Regex(String pattern) {
                this.pattern = Pattern.compile(pattern);
            }

            @Override
            public boolean matches(String segment) {
                return pattern.matcher(segment).matches();
            }

            @Override
            public String toString() {
                return "~" + escapeSegment(pattern.pattern());
            }
        }

        enum LeafMarker implements Segment {
            INSTANCE;

            @Override
            public boolean matches(String segment) {
                return false;
            }

            @Override
            public String toString() {
                return "_";
            }
        }

        boolean matches(String segment);
    }

    private final ImmutableList<Segment> segments;

    private TestPathSelector(ImmutableList<Segment> segments) {
        this.segments = segments;
    }

    /**
     * Determines whether the given path matches this selector.
     *
     * <p>
     * A path matches a selector if:
     * <ul>
     *     <li>The path is absolute.
     *     <li>Either:
     *     <ul>
     *     <li>The segment counts match, and each segment in the selector matches the corresponding segment in the path
     *     <li>The last segment in the selector is a leaf marker ({@code _}), the path has one less segment than the selector, and the preceding segments match.
     *     </ul>
     * </ul>
     * </p>
     *
     * @param path the path to check
     * @return {@code true} if the path matches this selector, {@code false} otherwise
     */
    public boolean matches(Path path) {
        if (!path.isAbsolute()) {
            return false;
        }
        List<String> pathSegments = path.segments();
        if (segments.isEmpty()) {
            return pathSegments.isEmpty();
        }
        List<Segment> selectorSegments = segments;
        if (selectorSegments.get(selectorSegments.size() - 1) == Segment.LeafMarker.INSTANCE) {
            selectorSegments = selectorSegments.subList(0, selectorSegments.size() - 1);
        }
        if (pathSegments.size() != selectorSegments.size()) {
            return false;
        }
        return Streams.zip(
            selectorSegments.stream(),
            pathSegments.stream(),
            Segment::matches
        ).noneMatch(segmentDidMatch -> segmentDidMatch);
    }

    /**
     * Returns an iterable over all ancestor selectors of this selector, starting from the immediate parent
     * up to the root selector.
     *
     * @return an iterable over all ancestor selectors of this selector
     */
    public Iterable<TestPathSelector> ancestors() {
        return () -> new AbstractIterator<TestPathSelector>() {
            private int currentSize = segments.size();

            @Nullable
            @Override
            protected TestPathSelector computeNext() {
                if (currentSize == 0) {
                    return endOfData();
                }
                currentSize--;
                return new TestPathSelector(segments.subList(0, currentSize));
            }
        };
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TestPathSelector)) {
            return false;
        }

        TestPathSelector that = (TestPathSelector) o;
        return segments.equals(that.segments);
    }

    @Override
    public int hashCode() {
        return segments.hashCode();
    }

    @Override
    public String toString() {
        return segments.stream()
            .map(Object::toString)
            .collect(Collectors.joining(":", ":", ""));
    }
}
