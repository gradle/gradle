/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.util.internal;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.function.TriConsumer;
import org.gradle.api.problems.ProblemId;
import org.gradle.api.problems.internal.GradleCoreProblemGroup;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Selects a single item from a collection based on a camel case pattern.
 */
public class NameMatcher {
    private final SortedSet<String> matches = new TreeSet<>();
    private final Set<String> candidates = new TreeSet<>();
    private String pattern;

    /**
     * Locates the best match for a camel case pattern in a key set of a map and returns the corresponding value.
     *
     * @return The matching item if exactly 1 match found, null if no matches or multiple matches.
     * @see #find(String, Collection)
     */
    public <T> T find(String pattern, Map<String, ? extends T> items) {
        String name = find(pattern, items.keySet());
        if (name != null) {
            return items.get(name);
        }
        return null;
    }

    /**
     * Locates the best match for a camel case pattern in a collection.
     * <p>
     * The pattern is expanded to match on camel case and on kebab case strings. For example, the pattern {@code gBD}
     * matches to {@code gradleBinaryDistribution} and {@code gradle-binary-distribution}.
     * <p>
     * The method will return {@code null} if the pattern is an empty string.
     * <p>
     * If the target collection contains the pattern string then the method omits the pattern matching and returns the pattern.
     *
     * @return The match if exactly 1 match found, null if no matches or multiple matches.
     */
    public String find(String pattern, Collection<String> items) {
        this.pattern = pattern;
        matches.clear();
        candidates.clear();

        if (items.contains(pattern)) {
            matches.add(pattern);
            return pattern;
        }

        if (pattern.isEmpty()) {
            return null;
        }

        Set<String> caseInsensitiveMatches = new TreeSet<>();
        String normalisedPattern = pattern.toUpperCase(Locale.ROOT);

        String camelCaseRegex = getCamelCasePatternForName(pattern);
        Pattern camelCasePattern = Pattern.compile(camelCaseRegex);
        Pattern caseInsensitiveCamelCasePattern = Pattern.compile(camelCaseRegex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        Set<String> caseInsensitivePrefixMatches = new TreeSet<>();
        Set<String> caseSensitiveCamelCaseMatches = new TreeSet<>();
        Set<String> caseInsensitiveCamelCaseMatches = new TreeSet<>();

        String kebabCaseRegex = getKebabCasePatternForName(pattern);
        Pattern kebabCasePattern = Pattern.compile(kebabCaseRegex);
        Pattern kebabCasePrefixPattern = Pattern.compile(kebabCaseRegex + KEBAB_CASE_PREFIX_TRAILING_PATTERN);
        Set<String> kebabCaseMatches = new TreeSet<>();
        Set<String> kebabCasePrefixMatches = new TreeSet<>();

        for (String candidate : items) {
            boolean found = false;

            if (candidate.equalsIgnoreCase(pattern)) {
                caseInsensitiveMatches.add(candidate);
                found = true;
            }
            if (camelCasePattern.matcher(candidate).matches()) {
                caseSensitiveCamelCaseMatches.add(candidate);
                found = true;
            }
            if (camelCasePattern.matcher(candidate).lookingAt()) {
                caseInsensitivePrefixMatches.add(candidate);
                found = true;
            }
            if (caseInsensitiveCamelCasePattern.matcher(candidate).lookingAt()) {
                caseInsensitiveCamelCaseMatches.add(candidate);
                found = true;
            }
            if (kebabCasePattern.matcher(candidate).matches()) {
                kebabCaseMatches.add(candidate);
                found = true;
            }
            if (kebabCasePrefixPattern.matcher(candidate).matches()) {
                kebabCasePrefixMatches.add(candidate);
                found = true;
            }
            if (!found && StringUtils.getLevenshteinDistance(normalisedPattern, candidate.toUpperCase(Locale.ROOT)) <= Math.min(3, pattern.length() / 2)) {
                candidates.add(candidate);
            }
        }

        if (!caseInsensitiveMatches.isEmpty()) {
            matches.addAll(caseInsensitiveMatches);
        } else if (!caseSensitiveCamelCaseMatches.isEmpty()) {
            matches.addAll(caseSensitiveCamelCaseMatches);
        } else if (!caseInsensitivePrefixMatches.isEmpty()) {
            matches.addAll(caseInsensitivePrefixMatches);
        } else if (kebabCaseMatches.isEmpty() && kebabCasePrefixMatches.isEmpty()) {
            matches.addAll(caseInsensitiveCamelCaseMatches);
        }

        if (!kebabCaseMatches.isEmpty()) {
            matches.addAll(kebabCaseMatches);
        } else if (!kebabCasePrefixMatches.isEmpty()) {
            matches.addAll(kebabCasePrefixMatches);
        }

        if (matches.size() == 1) {
            return matches.first();
        }

        return null;
    }

    private static final String CAMEL_CASE_TRAILING_PATTERN = "[\\p{javaLowerCase}]*";
    private static final String KEBAB_CASE_TRAILING_PATTERN = "[\\p{javaLowerCase}\\p{Digit}]*";
    private static final String KEBAB_CASE_PREFIX_TRAILING_PATTERN = "[\\p{javaLowerCase}\\p{Digit}-]*";

    private static final Pattern CAMEL_CASE_BOUNDARY_PATTERN = Pattern.compile("((^|\\p{Punct})\\p{javaLowerCase}+)|((\\p{javaUpperCase}|\\p{Digit})\\p{javaLowerCase}*)");
    private static final Pattern KEBAB_BOUNDARY_PATTERN = Pattern.compile("((^|\\p{Punct})\\p{javaLowerCase}+)|(\\p{javaUpperCase}\\p{javaLowerCase}*)");

    private static String getCamelCasePatternForName(String name) {
        return getPatternForName(name, CAMEL_CASE_BOUNDARY_PATTERN, (builder, pos, part) -> {
            builder.append(Pattern.quote(part));
            builder.append(CAMEL_CASE_TRAILING_PATTERN);
        });
    }

    private static String getKebabCasePatternForName(String name) {
        return getPatternForName(name, KEBAB_BOUNDARY_PATTERN, (builder, pos, part) -> {
            if (pos > 0) {
                builder.append('-');
            }
            builder.append(Pattern.quote(part.toLowerCase(Locale.ROOT)));
            builder.append(KEBAB_CASE_TRAILING_PATTERN);
        });
    }

    private static String getPatternForName(String name, Pattern boundaryPattern, TriConsumer<StringBuilder, Integer, String> addPattern) {
        Matcher matcher = boundaryPattern.matcher(name);
        int pos = 0;
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            String prefix = name.substring(pos, matcher.start());
            if (!prefix.isEmpty()) {
                builder.append(Pattern.quote(prefix));
            }
            addPattern.accept(builder, pos, matcher.group());
            pos = matcher.end();
        }
        if (pos < name.length()) {
            builder.append(Pattern.quote(name.substring(pos)));
        }
        return builder.toString();
    }

    /**
     * Returns all matches, when there were more than 1.
     *
     * @return The matches. Returns an empty set when there are no matches.
     */
    public Set<String> getMatches() {
        return matches;
    }

    /**
     * Returns the potential matches, if any.
     *
     * @return The matches. Returns an empty set when there are no potential matches.
     */
    public Set<String> getCandidates() {
        return candidates;
    }

    /**
     * Returns a formatted error message describing why the pattern matching failed.
     *
     * @return The error message.
     */
    public String formatErrorMessage(String singularItemDescription, Object container) {
        if (!matches.isEmpty()) {
            return String.format("%s '%s' is ambiguous in %s. Candidates are: %s.", singularItemDescription, pattern, container, GUtil.toString(matches));
        }
        if (!candidates.isEmpty()) {
            return String.format("%s '%s' not found in %s. Some candidates are: %s.", singularItemDescription, pattern, container, GUtil.toString(candidates));
        }
        return String.format("%s '%s' not found in %s.", singularItemDescription, pattern, container);
    }

    public ProblemId problemId() {
        if (!getMatches().isEmpty()) {
            return ProblemId.create("ambiguous-matches", "Ambiguous matches", GradleCoreProblemGroup.taskSelection());
        } else if (!getCandidates().isEmpty()) {
            return ProblemId.create("no-matches", "No matches", GradleCoreProblemGroup.taskSelection());
        } else {
            return ProblemId.create("selection-failed", "Selection failed", GradleCoreProblemGroup.taskSelection());
        }
    }
}
