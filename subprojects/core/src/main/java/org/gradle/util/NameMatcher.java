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

import org.apache.commons.lang.StringUtils;
import org.gradle.internal.deprecation.DeprecationLogger;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class is only here to maintain binary compatibility with existing plugins.
 *
 * @deprecated Will be removed in Gradle 9.0.
 */
@Deprecated
public class NameMatcher {

    static {
        DeprecationLogger.deprecateType(NameMatcher.class)
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(7, "org_gradle_util_reports_deprecations")
            .nagUser();
    }

    private final SortedSet<String> matches = new TreeSet<>();
    private final Set<String> candidates = new TreeSet<>();
    private String pattern;

    public NameMatcher() {
        // TODO log deprecation once nebula.dependency-lock plugin is fixed
    }

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

        if (pattern.length() == 0) {
            return null;
        }

        Pattern camelCasePattern = getPatternForName(pattern);
        Pattern normalisedCamelCasePattern = Pattern.compile(camelCasePattern.pattern(), Pattern.CASE_INSENSITIVE);
        String normalisedPattern = pattern.toUpperCase(Locale.ROOT);
        Pattern kebabCasePattern = getKebabCasePatternForName(pattern);
        Pattern kebabCasePrefixPattern = Pattern.compile(kebabCasePattern.pattern() + "[\\p{javaLowerCase}\\p{Digit}-]*");

        Set<String> caseInsensitiveMatches = new TreeSet<>();
        Set<String> caseSensitiveCamelCaseMatches = new TreeSet<>();
        Set<String> caseInsensitiveCamelCaseMatches = new TreeSet<>();
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
            if (normalisedCamelCasePattern.matcher(candidate).lookingAt()) {
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

    private static Pattern getPatternForName(String name) {
        Pattern boundaryPattern = Pattern.compile("((^|\\p{Punct})\\p{javaLowerCase}+)|(\\p{javaUpperCase}\\p{javaLowerCase}*)");
        Matcher matcher = boundaryPattern.matcher(name);
        int pos = 0;
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            String prefix = name.substring(pos, matcher.start());
            if (prefix.length() > 0) {
                builder.append(Pattern.quote(prefix));
            }
            builder.append(Pattern.quote(matcher.group()));
            builder.append("[\\p{javaLowerCase}\\p{Digit}]*");
            pos = matcher.end();
        }
        builder.append(Pattern.quote(name.substring(pos)));
        return Pattern.compile(builder.toString());
    }

    private static Pattern getKebabCasePatternForName(String name) {
        Pattern boundaryPattern = Pattern.compile("((^|\\p{Punct})\\p{javaLowerCase}+)|(\\p{javaUpperCase}\\p{javaLowerCase}*)");
        Matcher matcher = boundaryPattern.matcher(name);
        int pos = 0;
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            String prefix = name.substring(pos, matcher.start());
            if (prefix.length() > 0) {
                builder.append(Pattern.quote(prefix));
            }
            if (pos > 0) {
                builder.append('-');
            }
            builder.append(Pattern.quote(matcher.group().toLowerCase(Locale.ROOT)));
            builder.append("[\\p{javaLowerCase}\\p{Digit}]*");
            pos = matcher.end();
        }
        builder.append(Pattern.quote(name.substring(pos)));
        return Pattern.compile(builder.toString());
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
        String capItem = StringUtils.capitalize(singularItemDescription);
        if (!matches.isEmpty()) {
            return String.format("%s '%s' is ambiguous in %s. Candidates are: %s.", capItem, pattern, container, GUtil.toString(matches));
        }
        if (!candidates.isEmpty()) {
            return String.format("%s '%s' not found in %s. Some candidates are: %s.", capItem, pattern, container, GUtil.toString(candidates));
        }
        return String.format("%s '%s' not found in %s.", capItem, pattern, container);
    }

}
