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

package org.gradle.api.internal.artifacts.repositories;

import org.apache.ivy.core.IvyPatternHelper;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.internal.resource.ResourceException;
import org.gradle.api.internal.resource.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ResourceVersionLister implements VersionLister {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResourceVersionLister.class);
    public static final String REVISION_KEY = "revision";
    private static final String REVISION_TOKEN = "[" + REVISION_KEY + "]";
    public static final int REV_TOKEN_LENGTH = REVISION_TOKEN.length();

    private final ExternalResourceRepository repository;
    private final String fileSep;

    public ResourceVersionLister(ExternalResourceRepository repository) {
        this.repository = repository;
        this.fileSep = repository.getFileSeparator();
    }

    public VersionList getVersionList(ModuleRevisionId moduleRevisionId, String pattern, Artifact artifact) throws ResourceException, ResourceNotFoundException {
        ModuleRevisionId idWithoutRevision = ModuleRevisionId.newInstance(moduleRevisionId, IvyPatternHelper.getTokenString(IvyPatternHelper.REVISION_KEY));
        String partiallyResolvedPattern = IvyPatternHelper.substitute(pattern, idWithoutRevision, artifact);
        LOGGER.debug("Listing all in {}", partiallyResolvedPattern);
        try {
            return new DefaultVersionList(listRevisionToken(partiallyResolvedPattern));
        } catch (IOException e) {
            throw new ResourceException("Unable to load Versions", e);
        }
    }

    // lists all the values a revision token listed by a given url lister
    private List<String> listRevisionToken(String pattern) throws IOException {
        pattern = repository.standardize(pattern);
        if (!pattern.contains(REVISION_TOKEN)) {
            LOGGER.info("revision token not defined in pattern {}.", pattern);
            return Collections.emptyList();
        }
        String prefix = pattern.substring(0, pattern.indexOf(REVISION_TOKEN));
        if (revisionMatchesDirectoryName(pattern)) {
            return listAll(prefix);
        } else {
            int parentFolderSlashIndex = prefix.lastIndexOf(fileSep);
            String revisionParentFolder = parentFolderSlashIndex == -1 ? "" : prefix.substring(0, parentFolderSlashIndex);
            LOGGER.debug("using {} to list all in {} ", repository, revisionParentFolder);
            List<String> all = repository.list(revisionParentFolder);
            if (all == null) {
                throw new ResourceNotFoundException(String.format("Can not load %s for listing resources", revisionParentFolder));
            }
            LOGGER.debug("found {} urls", all.size());
            Pattern regexPattern = createRegexPattern(pattern, parentFolderSlashIndex);
            List ret = filterMatchedValues(all, regexPattern);
            LOGGER.debug("{} matched {}" + pattern, ret.size(), pattern);
            return ret;
        }
    }

    private List<String> filterMatchedValues(List<String> all, final Pattern p) {
        List<String> ret = new ArrayList<String>(all.size());
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
        int endNameIndex = pattern.indexOf(fileSep, prefixLastSlashIndex + 1);
        String namePattern;
        if (endNameIndex != -1) {
            namePattern = pattern.substring(prefixLastSlashIndex + 1, endNameIndex);
        } else {
            namePattern = pattern.substring(prefixLastSlashIndex + 1);
        }
        namePattern = namePattern.replaceAll("\\.", "\\\\.");

        String acceptNamePattern = ".*?"
                + namePattern.replaceAll("\\[revision\\]", "([^" + fileSep + "]+)")
                + "($|" + fileSep + ".*)";

        return Pattern.compile(acceptNamePattern);
    }

    private boolean revisionMatchesDirectoryName(String pattern) {
        int index = pattern.indexOf(REVISION_TOKEN);
        boolean patternStartsWithRevisionToken = index == 0;
        boolean revisionTokenisFollowedByFileSeparator = pattern.contains(REVISION_TOKEN + fileSep);
        boolean revisionTokenisPrefixedByFileSeparator = pattern.contains(fileSep + REVISION_TOKEN);
        return ((pattern.length() <= index + REV_TOKEN_LENGTH) || revisionTokenisFollowedByFileSeparator)
                && (patternStartsWithRevisionToken || revisionTokenisPrefixedByFileSeparator);
    }


    private List<String> listAll(String parent) throws IOException {
        LOGGER.debug("using {} to list all in {}", repository, parent);
        List<String> fullPaths = repository.list(parent);
        if (fullPaths == null) {
            throw new ResourceNotFoundException(String.format("Unable to load %s for listing versions", parent));
        }
        LOGGER.debug("found {} resources", fullPaths.size());
        List<String> names = extractVersionInfoFromPaths(fullPaths);
        return names;
    }

    private List<String> extractVersionInfoFromPaths(List<String> paths) {
        List<String> ret = new ArrayList<String>(paths.size());
        for (String fullpath : paths) {
            if (fullpath.endsWith(fileSep)) {
                fullpath = fullpath.substring(0, fullpath.length() - 1);
            }
            int slashIndex = fullpath.lastIndexOf(fileSep);
            ret.add(fullpath.substring(slashIndex + 1));
        }
        return ret;
    }
}
