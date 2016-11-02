/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.api.internal.artifacts.configurations;

import com.google.common.collect.ImmutableList;
import org.gradle.api.artifacts.ConfigurationAttributeMatcher;
import org.gradle.api.artifacts.ConfigurationAttributesMatchingStrategy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class ConfigurationAttributeMatchingStrategies {
    public static <T> List<T> findBestMatches(final ConfigurationAttributesMatchingStrategy strategy, Map<String, String> sourceAttributes, Map<T, Map<String, String>> candidates) {
        Set<String> sourceAttributeNames = sourceAttributes.keySet();
        Map<T, MatchDetails> remainingCandidatesAfterRequired = new LinkedHashMap<T, MatchDetails>(candidates.size());
        for (Map.Entry<T, Map<String, String>> entry : candidates.entrySet()) {
            remainingCandidatesAfterRequired.put(entry.getKey(), new MatchDetails(entry.getValue()));
        }

        filterCandidates((ConfigurationAttributesMatchingStrategyInternal) strategy, sourceAttributes, remainingCandidatesAfterRequired, sourceAttributeNames);
        List<T> singleMatch = findBestMatch(remainingCandidatesAfterRequired);
        if (singleMatch != null) {
            return singleMatch;
        }
        if (remainingCandidatesAfterRequired.isEmpty()) {
            return Collections.emptyList();
        }
        return ImmutableList.copyOf(remainingCandidatesAfterRequired.keySet());
    }

    private static <T> List<T> findBestMatch(Map<T, MatchDetails> remainingCandidates) {
        if (remainingCandidates.size() == 1) {
            return Collections.singletonList(remainingCandidates.keySet().iterator().next());
        }
        int bestScore = Integer.MAX_VALUE;
        int bestCount = 0;
        T best = null;
        for (Map.Entry<T, MatchDetails> entry : remainingCandidates.entrySet()) {
            int score = entry.getValue().score;
            if (score < bestScore) {
                bestScore = score;
                best = entry.getKey();
                bestCount = 1;
            } else if (score == bestScore) {
                bestCount++;
            }
        }
        if (bestCount == 1) {
            return Collections.singletonList(best);
        }
        return null;
    }

    private static <T> void filterCandidates(ConfigurationAttributesMatchingStrategyInternal strategy, Map<String, String> sourceAttributes, Map<T, MatchDetails> candidates, Collection<String> requiredAttributes) {
        for (String requiredAttribute : requiredAttributes) {
            String requestedValue = sourceAttributes.get(requiredAttribute);
            Iterator<Map.Entry<T, MatchDetails>> it = candidates.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<T, MatchDetails> entry = it.next();
                MatchDetails details = entry.getValue();
                Map<String, String> candidateAttributes = details.attributes;
                boolean hasAttribute = candidateAttributes.containsKey(requiredAttribute);
                ConfigurationAttributeMatcher matcher = strategy.getAttributeMatcher(requiredAttribute);
                String defaultValue = matcher.defaultValue(requestedValue);
                if (!hasAttribute && defaultValue == null) {
                    it.remove();
                } else {
                    int cmp = matcher.score(requestedValue, hasAttribute ? candidateAttributes.get(requiredAttribute) : defaultValue);
                    if (cmp < 0) {
                        it.remove();
                    }
                    details.score += cmp;
                    if (!hasAttribute) {
                        details.partial = true;
                    }
                }
            }
        }
        if (candidates.isEmpty()) {
            return;
        }
        // we need to remove partial matches from the result set if and only if exact matches were found
        List<Map.Entry<T, MatchDetails>> exactMatches = new ArrayList<Map.Entry<T, MatchDetails>>(candidates.size());
        List<Map.Entry<T, MatchDetails>> partialMatches = new ArrayList<Map.Entry<T, MatchDetails>>(candidates.size());
        for (Map.Entry<T, MatchDetails> entry : candidates.entrySet()) {
            if (entry.getValue().partial) {
                partialMatches.add(entry);
            } else {
                exactMatches.add(entry);
            }
        }
        if (!exactMatches.isEmpty()) {
            for (Map.Entry<T, MatchDetails> match : partialMatches) {
                candidates.remove(match.getKey());
            }
        }
    }

    private static class MatchDetails {
        final Map<String, String> attributes;
        int score;
        boolean partial = false;

        private MatchDetails(Map<String, String> attributes) {
            this.attributes = attributes;
        }
    }
}
