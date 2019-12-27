/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.writer;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.verification.verifier.DependencyVerifierBuilder;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This class is responsible for "normalizing" trusted PGP keys.
 * It tries to identify common super modules/groups/etc... which can
 * then be moved globally.
 *
 * It's worth noting that the result is _less strict_ than keeping all
 * trusted PGP keys at the artifact level, but it significantly reduces
 * the configuration file size and helps maintenance.
 */
class PgpKeyGrouper {
    private static final Splitter GROUP_SPLITTER = Splitter.on(".");
    private static final String GROUP_SUFFIX = "($|([.].*))";
    private static final Joiner GROUP_JOINER = Joiner.on("[.]");

    private final DependencyVerifierBuilder verificationsBuilder;
    private final Set<VerificationEntry> entriesToBeWritten;

    PgpKeyGrouper(DependencyVerifierBuilder dependencyVerifierBuilder, Set<VerificationEntry> entriesToBeWritten) {
        this.verificationsBuilder = dependencyVerifierBuilder;
        this.entriesToBeWritten = entriesToBeWritten;
    }

    public void performPgpKeyGrouping() {
        Multimap<String, PgpEntry> keysToEntries = groupEntriesByPgpKey();
        keysToEntries.asMap()
            .entrySet()
            .forEach(e -> {
                Collection<PgpEntry> pgpKeys = e.getValue();
                if (pgpKeys.size() > 1) {
                    // if there's only one entry, we won't "normalize" into globally trusted keys
                    List<ModuleComponentIdentifier> moduleComponentIds = pgpKeys.stream()
                        .map(PgpEntry::getId)
                        .map(ModuleComponentArtifactIdentifier::getComponentIdentifier)
                        .distinct()
                        .collect(Collectors.toList());
                    if (moduleComponentIds.size() == 1) {
                        groupByModuleComponentId(e, moduleComponentIds);
                    } else {
                        List<ModuleIdentifier> moduleIds = moduleComponentIds.stream()
                            .map(ModuleComponentIdentifier::getModuleIdentifier)
                            .distinct()
                            .collect(Collectors.toList());
                        if (moduleIds.size() == 1) {
                            groupByModuleId(e, moduleIds);
                        } else {
                            List<String> groups = moduleIds.stream()
                                .map(ModuleIdentifier::getGroup)
                                .distinct()
                                .collect(Collectors.toList());
                            if (groups.size() == 1) {
                                groupByGroupOnly(e, groups);
                            } else {
                                groupUsingRegex(e, groups);
                                processRemainingGroups(e, groups);
                            }
                        }
                    }
                }
            });
    }

    private void processRemainingGroups(Map.Entry<String, Collection<PgpEntry>> e, List<String> groups) {
        String keyId = e.getKey();
        List<PgpEntry> remainingUntouched = e.getValue()
            .stream()
            .filter(p -> p.doesNotDeclareKeyGlobally(keyId))
            .collect(Collectors.toList());
        for (String group : groups) {
            long count = remainingUntouched.stream().filter(p -> p.getGroup().equals(group)).count();
            if (count>1) {
                // a key is at least used in 2 artifacts
                verificationsBuilder.addTrustedKey(
                    keyId,
                    group,
                    null,
                    null,
                    null,
                    false
                );
                remainingUntouched
                    .stream()
                    .filter(p -> p.getGroup().equals(group))
                    .forEach(p -> p.keyDeclaredGlobally(keyId));
            }
        }
    }

    private void groupUsingRegex(Map.Entry<String, Collection<PgpEntry>> e, List<String> groups) {
        String keyID = e.getKey();
        List<List<String>> commonPrefixes = tryComputeCommonPrefixes(groups);
        for (List<String> prefix : commonPrefixes) {
            String groupRegex = "^" + GROUP_JOINER.join(prefix) + GROUP_SUFFIX;
            verificationsBuilder.addTrustedKey(
                e.getKey(),
                groupRegex,
                null,
                null,
                null,
                true
            );
            for (PgpEntry pgpEntry : e.getValue()) {
                if (pgpEntry.getGroup().matches(groupRegex)) {
                    pgpEntry.keyDeclaredGlobally(keyID);
                }
            }
        }
    }

    // Tries to find the common super-group for a list of groups
    // For example given ["org.foo", "org.foo.bar", "org.foo.baz"] it will group using "org.foo.*"
    static List<List<String>> tryComputeCommonPrefixes(List<String> groups) {
        List<List<String>> splitGroups = groups.stream()
            .map(group -> GROUP_SPLITTER.splitToList(group))
            .sorted(Comparator.comparing(List::size))
            .collect(Collectors.toList());
        List<String> shortest = splitGroups.get(0);
        if (shortest.size() < 2) {
            // we need at least a prefix of 2 elements, like "com.mycompany", to perform grouping
            return Collections.emptyList();
        }
        List<List<String>> commonPrefixes = Lists.newArrayList();
        List<List<String>> remainder = Lists.newArrayList(splitGroups);
        List<List<String>> previous = null;
        while (!remainder.isEmpty()) {
            previous = Lists.newArrayList(remainder);
            shortest = remainder.get(0);
            int prefixLen = 2;
            List<String> prefix = shortest.subList(0, prefixLen);
            List<String> commonPrefix = null;
            List<List<String>> candidatesWithSamePrefix = Lists.newArrayList(remainder);
            while ((candidatesWithSamePrefix = samePrefix(prefixLen, prefix, candidatesWithSamePrefix)).size() > 1) {
                remainder.removeAll(candidatesWithSamePrefix);
                commonPrefix = prefix;
                prefixLen++;
                if (prefixLen <= shortest.size()) {
                    prefix = shortest.subList(0, prefixLen);
                } else {
                    break;
                }
            }
            if (commonPrefix != null) {
                commonPrefixes.add(commonPrefix);
            }
            if (remainder.equals(previous)) {
                // could do nothing with the first, let's go with the next one
                remainder.remove(0);
            }
        }
        return commonPrefixes;
    }

    private static List<List<String>> samePrefix(int prefixLen, List<String> prefix, List<List<String>> candidates) {
        return candidates.stream().filter(groups -> groups.subList(0, prefixLen).equals(prefix)).collect(Collectors.toList());
    }

    private void markKeyDeclaredGlobally(Map.Entry<String, Collection<PgpEntry>> e) {
        String keyID = e.getKey();
        for (PgpEntry pgpEntry : e.getValue()) {
            pgpEntry.keyDeclaredGlobally(keyID);
        }
    }

    private void groupByGroupOnly(Map.Entry<String, Collection<PgpEntry>> e, List<String> groups) {
        String group = groups.get(0);
        verificationsBuilder.addTrustedKey(
            e.getKey(),
            group,
            null,
            null,
            null,
            false
        );
        markKeyDeclaredGlobally(e);
    }

    private void groupByModuleId(Map.Entry<String, Collection<PgpEntry>> e, List<ModuleIdentifier> moduleIds) {
        ModuleIdentifier mi = moduleIds.get(0);
        verificationsBuilder.addTrustedKey(
            e.getKey(),
            mi.getGroup(),
            mi.getName(),
            null,
            null,
            false
        );
        markKeyDeclaredGlobally(e);
    }

    private void groupByModuleComponentId(Map.Entry<String, Collection<PgpEntry>> e, List<ModuleComponentIdentifier> moduleComponentIds) {
        ModuleComponentIdentifier mci = moduleComponentIds.get(0);
        verificationsBuilder.addTrustedKey(
            e.getKey(),
            mci.getGroup(),
            mci.getModule(),
            mci.getVersion(),
            null,
            false
        );
        markKeyDeclaredGlobally(e);
    }

    private Multimap<String, PgpEntry> groupEntriesByPgpKey() {
        Multimap<String, PgpEntry> keysToEntries = HashMultimap.create();
        entriesToBeWritten.stream()
            .filter(PgpEntry.class::isInstance)
            .map(PgpEntry.class::cast)
            .filter(e -> !e.getTrustedKeys().isEmpty())
            .forEach(e -> {
                for (String trustedKey : e.getTrustedKeys()) {
                    keysToEntries.put(trustedKey, e);
                }
            });
        return keysToEntries;
    }
}
