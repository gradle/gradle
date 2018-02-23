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

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;

/**
 * Selects a single item from a list of candidates based on a camel-case pattern.
 */
public class NameMatcher {
    private final SortedSet<String> matches = new TreeSet<String>();
    private final Set<String> candidates = new TreeSet<String>();
    private String pattern;

    /**
     * Locates the best match for the given pattern in the given set of candidate items.
     *
     * @return The matching item if exactly 1 match found, null if no matches or multiple matches.
     */
    public <T> T find(String pattern, Map<String, ? extends T> items) {
        String name = find(pattern, items.keySet());
        if (name != null) {
            return items.get(name);
        }
        return null;
    }

    /**
     * Locates the best match for the given pattern in the given set of candidate items.
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
        String normalisedPattern = pattern.toUpperCase();

        Set<String> caseInsensitiveMatches = new TreeSet<String>();
        Set<String> caseSensitiveCamelCaseMatches = new TreeSet<String>();
        Set<String> caseInsensitiveCamelCaseMatches = new TreeSet<String>();

        for (String candidate : items) {
            if (candidate.equalsIgnoreCase(pattern)) {
                caseInsensitiveMatches.add(candidate);
            }
            if (camelCasePattern.matcher(candidate).matches()) {
                caseSensitiveCamelCaseMatches.add(candidate);
                continue;
            }
            if (normalisedCamelCasePattern.matcher(candidate).lookingAt()) {
                caseInsensitiveCamelCaseMatches.add(candidate);
                continue;
            }
            if (StringUtils.getLevenshteinDistance(normalisedPattern, candidate.toUpperCase()) <= Math.min(3, pattern.length() / 2)) {
                candidates.add(candidate);
            }
        }

        if (!caseInsensitiveMatches.isEmpty()) {
            matches.addAll(caseInsensitiveMatches);
        } else if (!caseSensitiveCamelCaseMatches.isEmpty()) {
            matches.addAll(caseSensitiveCamelCaseMatches);
        } else {
            matches.addAll(caseInsensitiveCamelCaseMatches);
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
        builder.append(Pattern.quote(name.substring(pos, name.length())));
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
