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
import org.gradle.api.Describable;
import org.gradle.util.Path;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A "selector" for test paths, which can match against multiple {@link Path}s.
 *
 * <p>
 * No relative paths are allowed, to simplify matching logic.
 * </p>
 *
 * <p>
 * A selector is constructed from a list of {@link Segment}s.
 * </p>
 *
 * @param segments the segments of the selector, in order
 */
public record TestPathSelector(ImmutableList<Segment> segments) {

    public TestPathSelector {
        boolean seenClassMarker = false;
        for (int i = 0; i < segments.size(); i++) {
            Segment segment = segments.get(i);
            if (i < segments.size() - 1 && isFurtherLeafNodes(segment)) {
                throw new IllegalArgumentException("FurtherLeafNodes segment must be the last segment, but was at index " + i + " of " + segments.size());
            }
            if (segment instanceof Segment.ClassMarker) {
                if (seenClassMarker) {
                    throw new IllegalArgumentException("Only one ClassMarker is allowed per selector");
                }
                seenClassMarker = true;
            }
        }
    }

    public static TestPathSelector of(Segment... segments) {
        return new TestPathSelector(ImmutableList.copyOf(segments));
    }

    public sealed interface MatchResult extends Describable {
        enum Matched implements MatchResult {
            INSTANCE;

            @Override
            public String getDisplayName() {
                return "matched";
            }
        }

        enum NotAbsolute implements MatchResult {
            INSTANCE;

            @Override
            public String getDisplayName() {
                return "path is not absolute";
            }
        }

        record SegmentCountMismatch(int expected, int actual) implements MatchResult {
            @Override
            public String getDisplayName() {
                return String.format("segment count mismatch (expected: %d, actual: %d)", expected, actual);
            }
        }

        record SegmentMismatch(int segmentIndex, String expectedPhrase, String actual) implements MatchResult {
            @Override
            public String getDisplayName() {
                return "segment " + segmentIndex + " mismatch (expected '" + expectedPhrase + "', got '" + actual + "')";
            }
        }

        default boolean isMatch() {
            return this == Matched.INSTANCE;
        }
    }

    // These would live in Segment, but Groovy cannot static-import from an interface :/

    /**
     * {@return a literal segment matching the given exact string}
     */
    public static Segment.Literal literal(String value) {
        return new Segment.Literal(value);
    }

    /**
     * {@return a regex segment matching the given pattern}
     */
    public static Segment.Regex regex(String pattern) {
        return new Segment.Regex(Pattern.compile(pattern));
    }

    /**
     * {@return a class marker wrapping the given segment}
     */
    public static Segment.ClassMarker classMarker(Segment inner) {
        return new Segment.ClassMarker(inner);
    }

    /**
     * {@return the singleton instance of the further-leaf-nodes segment}
     */
    public static Segment.FurtherLeafNodes furtherLeafNodes() {
        return Segment.FurtherLeafNodes.INSTANCE;
    }

    public sealed interface Segment {

        /**
         * Matches an exact string.
         *
         * <p>Using the constructor is discouraged, prefer {@link #literal(String)}.</p>
         */
        record Literal(String value) implements Segment {
            @Override
            public boolean matches(String segment) {
                return value.equals(segment);
            }

            @Override
            public String toString() {
                return value;
            }
        }

        /**
         * Matches a regular expression.
         *
         * <p>Using the constructor is discouraged, prefer {@link #regex(String)}.</p>
         */
        record Regex(Pattern pattern) implements Segment {
            @Override
            public boolean matches(String segment) {
                return pattern.matcher(segment).matches();
            }

            @Override
            public String toString() {
                return "Regex(" + pattern.pattern() + ")";
            }

            // Have to override equals and hashCode as Pattern doesn't implement them.
            // We assume equality based on the pattern string and flags.

            @Override
            public boolean equals(Object o) {
                return o instanceof Regex regex
                    && pattern.pattern().equals(regex.pattern.pattern())
                    && pattern.flags() == regex.pattern.flags();
            }

            @Override
            public int hashCode() {
                return Objects.hash(pattern.pattern(), pattern.flags());
            }
        }

        /**
         * Wraps another segment, marking it as the class segment for JUnit XML matching.
         *
         * <p>Using the constructor is discouraged, prefer {@link #classMarker(Segment)}.</p>
         */
        record ClassMarker(Segment inner) implements Segment {
            public ClassMarker {
                if (inner instanceof ClassMarker) {
                    throw new IllegalArgumentException("ClassMarker cannot be nested");
                }
            }

            @Override
            public boolean matches(String segment) {
                return inner.matches(segment);
            }

            @Override
            public String toString() {
                return "ClassMarker(" + inner + ")";
            }
        }

        /**
         * A sentinel that may only appear as the last segment, indicating that there are
         * further "leaf" nodes below the specified path. It will not match those leaf nodes
         * themselves, but this is necessary to allow interoperability between selecting tests
         * from HTML and JUnit XML reports with the same code, as JUnit XML reports lack
         * intermediate nodes.
         *
         * <p>Use {@link #furtherLeafNodes()} to obtain the singleton instance.</p>
         */
        final class FurtherLeafNodes implements Segment {
            /**
             * Singleton instance of {@link FurtherLeafNodes}.
             */
            private static final FurtherLeafNodes INSTANCE = new FurtherLeafNodes();

            private FurtherLeafNodes() {
            }

            // Never called — matchableSegments() excludes FurtherLeafNodes before matching
            @Override
            public boolean matches(String segment) {
                throw new UnsupportedOperationException("FurtherLeafNodes cannot be matched against a path segment");
            }

            @Override
            public String toString() {
                return "FurtherLeafNodes";
            }
        }

        boolean matches(String segment);
    }

    /**
     * Whether this selector indicates further leaf nodes below the specified path.
     */
    public boolean hasFurtherLeafNodes() {
        return !segments.isEmpty() && isFurtherLeafNodes(segments.get(segments.size() - 1));
    }

    private static boolean isFurtherLeafNodes(Segment segment) {
        return segment instanceof Segment.FurtherLeafNodes
            || (segment instanceof Segment.ClassMarker cm && isFurtherLeafNodes(cm.inner()));
    }

    private List<Segment> matchableSegments() {
        return hasFurtherLeafNodes() ? segments.subList(0, segments.size() - 1) : segments;
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
     *     <li>The last segment in the selector is a {@link Segment.FurtherLeafNodes}, the path has one fewer segment than
     *         the selector, and the preceding segments match.
     *     </ul>
     * </ul>
     *
     * @param path the path to check
     * @return {@link MatchResult.Matched} if the path matches this selector; a descriptive {@link MatchResult} otherwise
     */
    public MatchResult matches(Path path) {
        if (!path.isAbsolute()) {
            return MatchResult.NotAbsolute.INSTANCE;
        }
        List<String> pathSegments = path.segments();
        List<Segment> selectorSegments = matchableSegments();
        if (pathSegments.size() != selectorSegments.size()) {
            return new MatchResult.SegmentCountMismatch(selectorSegments.size(), pathSegments.size());
        }
        for (int i = 0; i < selectorSegments.size(); i++) {
            Segment selectorSegment = selectorSegments.get(i);
            String pathSegment = pathSegments.get(i);
            if (!selectorSegment.matches(pathSegment)) {
                return new MatchResult.SegmentMismatch(i, selectorSegment.toString(), pathSegment);
            }
        }
        return MatchResult.Matched.INSTANCE;
    }

    /**
     * Returns an iterable over all ancestor selectors of this selector, starting from the immediate parent
     * up to the root selector.
     *
     * <p>
     * Ancestors always end with a {@link Segment.FurtherLeafNodes}, as there is always this selector which was
     * either a leaf or has further leaf nodes. For example, {@code [a, b, c]} produces
     * {@code [a, b, FurtherLeafNodes]}, {@code [a, FurtherLeafNodes]}, {@code [FurtherLeafNodes]}.
     * </p>
     *
     * @return an iterable over all ancestor selectors of this selector
     */
    public Iterable<TestPathSelector> ancestors() {
        return () -> new AbstractIterator<>() {
            private final List<Segment> baseSegments = matchableSegments();
            private final int classMarkerIndex = findClassMarkerIndex(segments);
            private int currentSize = baseSegments.size();

            @Override
            @Nullable
            protected TestPathSelector computeNext() {
                if (currentSize == 0) {
                    return endOfData();
                }
                currentSize--;
                Segment leafSegment = Segment.FurtherLeafNodes.INSTANCE;
                if (classMarkerIndex >= 0 && classMarkerIndex >= currentSize) {
                    // The ClassMarker's segment was trimmed; propagate it to the leaf position
                    leafSegment = new Segment.ClassMarker(leafSegment);
                }
                ImmutableList<Segment> ancestorSegments = ImmutableList.<Segment>builderWithExpectedSize(currentSize + 1)
                    .addAll(baseSegments.subList(0, currentSize))
                    .add(leafSegment)
                    .build();
                return new TestPathSelector(ancestorSegments);
            }
        };
    }

    private static int findClassMarkerIndex(List<Segment> segments) {
        for (int i = 0; i < segments.size(); i++) {
            if (segments.get(i) instanceof Segment.ClassMarker) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public String toString() {
        if (segments.isEmpty()) {
            return ":";
        }
        StringBuilder sb = new StringBuilder();
        for (Segment segment : segments) {
            sb.append(':');
            sb.append(segment);
        }
        return sb.toString();
    }
}
