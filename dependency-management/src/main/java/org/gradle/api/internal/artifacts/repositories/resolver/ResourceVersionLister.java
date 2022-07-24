/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.artifacts.repositories.resolver;

import com.google.common.collect.Lists;
import org.apache.ivy.core.IvyPatternHelper;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.resolve.result.BuildableModuleVersionListingResolveResult;
import org.gradle.internal.resource.ExternalResourceName;
import org.gradle.internal.resource.ExternalResourceRepository;
import org.gradle.internal.resource.ResourceExceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ResourceVersionLister implements VersionLister {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResourceVersionLister.class);
    private static final String REVISION_TOKEN = IvyPatternHelper.getTokenString(IvyPatternHelper.REVISION_KEY);
    public static final int REV_TOKEN_LENGTH = REVISION_TOKEN.length();

    private final ExternalResourceRepository repository;
    private final String fileSeparator = "/";
    private final Map<ExternalResourceName, List<String>> directoriesToList = new HashMap<>();

    public ResourceVersionLister(ExternalResourceRepository repository) {
        this.repository = repository;
    }

    @Override
    public void listVersions(ModuleIdentifier module, IvyArtifactName artifact, List<ResourcePattern> patterns, BuildableModuleVersionListingResolveResult result) {
        List<String> collector = Lists.newArrayList();
        List<ResourcePattern> filteredPatterns = filterDuplicates(patterns);
        Map<ResourcePattern, ExternalResourceName> versionListPatterns = filteredPatterns.stream().collect(Collectors.toMap(pattern -> pattern, pattern -> pattern.toVersionListPattern(module, artifact)));
        for (ResourcePattern pattern : filteredPatterns) {
            visit(pattern, versionListPatterns, collector, result);
        }
        if (!collector.isEmpty()) {
            result.listed(collector);
        }
    }

    private List<ResourcePattern> filterDuplicates(List<ResourcePattern> patterns) {
        if (patterns.size() <= 1) {
            return patterns;
        }
        List<ResourcePattern> toRemove = new ArrayList<>(patterns.size());
        for (int i = 0; i < patterns.size() - 1; i++) {
            ResourcePattern first = patterns.get(i);
            if (toRemove.contains(first)) {
                continue;
            }
            for (int j = i + 1; j < patterns.size(); j++) {
                ResourcePattern second = patterns.get(j);
                if (first.getPattern().startsWith(second.getPattern())) {
                    toRemove.add(second);
                } else if (second.getPattern().startsWith(first.getPattern())) {
                    toRemove.add(first);
                    break;
                }
            }
        }
        if (toRemove.isEmpty()) {
            return patterns;
        } else {
            ArrayList<ResourcePattern> result = new ArrayList<>(patterns);
            result.removeAll(toRemove);
            return result;
        }
    }

    private void visit(ResourcePattern pattern, Map<ResourcePattern, ExternalResourceName> versionListPatterns, List<String> collector, BuildableModuleVersionListingResolveResult result) {
        ExternalResourceName versionListPattern = versionListPatterns.get(pattern);
        LOGGER.debug("Listing all in {}", versionListPattern);
        try {
            collector.addAll(listRevisionToken(versionListPattern, result, versionListPatterns));
        } catch (Exception e) {
            throw ResourceExceptions.failure(versionListPattern.getUri(), String.format("Could not list versions using %s.", pattern), e);
        }
    }

    // lists all the values a revision token listed by a given url lister
    private List<String> listRevisionToken(ExternalResourceName versionListPattern, BuildableModuleVersionListingResolveResult result, Map<ResourcePattern, ExternalResourceName> versionListPatterns) {
        String pattern = versionListPattern.getPath();
        if (!pattern.contains(REVISION_TOKEN)) {
            LOGGER.debug("revision token not defined in pattern {}.", pattern);
            return Collections.emptyList();
        }
        String prefix = pattern.substring(0, pattern.indexOf(REVISION_TOKEN));
        List<String> listedVersions;
        if (revisionMatchesDirectoryName(pattern)) {
            ExternalResourceName parent = versionListPattern.getRoot().resolve(prefix);
            listedVersions = listAll(parent, result);
        } else {
            int parentFolderSlashIndex = prefix.lastIndexOf(fileSeparator);
            String revisionParentFolder = parentFolderSlashIndex == -1 ? "" : prefix.substring(0, parentFolderSlashIndex + 1);
            ExternalResourceName parent = versionListPattern.getRoot().resolve(revisionParentFolder);
            LOGGER.debug("using {} to list all in {} ", repository, revisionParentFolder);
            result.attempted(parent);
            List<String> all = listWithCache(parent);
            if (all == null) {
                return Collections.emptyList();
            }
            LOGGER.debug("found {} urls", all.size());
            Pattern regexPattern = createRegexPattern(pattern, parentFolderSlashIndex);
            listedVersions = filterMatchedValues(all, regexPattern);
            LOGGER.debug("{} matched {}", listedVersions.size(), pattern);
        }
        if (versionListPatterns.size() > 1) {
            // Verify that none of the listed "versions" do match another pattern
            return filterOutMatchesWithOverlappingPatterns(listedVersions, versionListPattern, versionListPatterns.values());
        }
        return listedVersions;
    }

    private List<String> filterOutMatchesWithOverlappingPatterns(List<String> listedVersions, ExternalResourceName currentVersionListPattern, Collection<ExternalResourceName> versionListPatterns) {
        List<String> remaining = Lists.newArrayList(listedVersions);
        for (ExternalResourceName otherVersionListPattern : versionListPatterns) {
            if (otherVersionListPattern != currentVersionListPattern) {
                String patternPath = otherVersionListPattern.getPath();
                Pattern regexPattern = toControlRegexPattern(patternPath);
                List<String> matching = listedVersions.stream()
                    .filter(version -> regexPattern.matcher(currentVersionListPattern.getPath().replace(REVISION_TOKEN, version)).matches())
                    .collect(Collectors.toList());
                if (!matching.isEmpty()) {
                    LOGGER.debug("Filtered out {} from results for overlapping match with {}", matching, otherVersionListPattern);
                    remaining.removeAll(matching);
                }
            }
        }
        return remaining;
    }

    private List<String> filterMatchedValues(List<String> all, final Pattern p) {
        List<String> ret = new ArrayList<>(all.size());
        for (String path : all) {
            Matcher m = p.matcher(path);
            if (m.matches()) {
                String value = m.group(1);
                ret.add(value);
            }
        }
        return ret;
    }

    private Pattern createRegexPattern(String pattern, int prefixLastSlashIndex) {
        int endNameIndex = pattern.indexOf(fileSeparator, prefixLastSlashIndex + 1);
        String namePattern;
        if (endNameIndex != -1) {
            namePattern = pattern.substring(prefixLastSlashIndex + 1, endNameIndex);
        } else {
            namePattern = pattern.substring(prefixLastSlashIndex + 1);
        }
        namePattern = namePattern.replaceAll("\\.", "\\\\.");
        String acceptNamePattern = namePattern.replaceAll("\\[revision]", "(.+)");
        return Pattern.compile(acceptNamePattern);
    }

    private Pattern toControlRegexPattern(String pattern) {
        pattern = pattern.replaceAll("\\.", "\\\\.");

        // Creates a control regexp pattern where extra revision tokens _must_ have the same value as the original one
        String acceptNamePattern = pattern.replaceFirst("\\[revision]", "(.+)")
            .replaceAll("\\[revision]", "\1");
        return Pattern.compile(acceptNamePattern);
    }

    private boolean revisionMatchesDirectoryName(String pattern) {
        int startToken = pattern.indexOf(REVISION_TOKEN);
        if (startToken > 0 && !pattern.startsWith(fileSeparator, startToken - 1)) {
            // previous character is not a separator
            return false;
        }
        int endToken = startToken + REV_TOKEN_LENGTH;
        // next character is not a separator
        return endToken >= pattern.length() || pattern.startsWith(fileSeparator, endToken);
    }

    private List<String> listAll(ExternalResourceName parent, BuildableModuleVersionListingResolveResult result) {
        LOGGER.debug("using {} to list all in {}", repository, parent);
        result.attempted(parent.toString());
        List<String> paths = listWithCache(parent);
        if (paths == null) {
            return Collections.emptyList();
        }
        LOGGER.debug("found {} resources", paths.size());
        return paths;
    }

    @Nullable
    private List<String> listWithCache(ExternalResourceName parent) {
        if (directoriesToList.containsKey(parent)) {
            return directoriesToList.get(parent);
        } else {
            List<String> result = repository.resource(parent).list();
            directoriesToList.put(parent, result);
            return result;
        }
    }
}
