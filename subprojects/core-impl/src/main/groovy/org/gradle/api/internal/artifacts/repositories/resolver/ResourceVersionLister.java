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

import org.apache.ivy.core.IvyPatternHelper;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.internal.artifacts.metadata.ModuleVersionArtifactMetaData;
import org.gradle.api.internal.externalresource.transport.ExternalResourceRepository;
import org.gradle.api.internal.resource.ResourceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ResourceVersionLister implements VersionLister {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResourceVersionLister.class);
    private static final String REVISION_TOKEN = IvyPatternHelper.getTokenString(IvyPatternHelper.REVISION_KEY);
    public static final int REV_TOKEN_LENGTH = REVISION_TOKEN.length();

    private final ExternalResourceRepository repository;
    private final String fileSeparator = "/";

    public ResourceVersionLister(ExternalResourceRepository repository) {
        this.repository = repository;
    }

    public VersionList getVersionList(final ModuleIdentifier module) {
        return new DefaultVersionList() {
            final Set<String> directories = new HashSet<String>();

            public void visit(ResourcePattern resourcePattern, ModuleVersionArtifactMetaData artifact) throws ResourceException {
                String partiallyResolvedPattern = resourcePattern.toVersionListPattern(artifact);
                LOGGER.debug("Listing all in {}", partiallyResolvedPattern);
                try {
                    List<String> versionStrings = listRevisionToken(partiallyResolvedPattern);
                    for (String versionString : versionStrings) {
                        add(new ListedVersion(versionString, resourcePattern));
                    }
                } catch (ResourceException e) {
                    throw e;
                } catch (Exception e) {
                    throw new ResourceException(String.format("Could not list versions using %s.", resourcePattern), e);
                }
            }

            // lists all the values a revision token listed by a given url lister
            private List<String> listRevisionToken(String pattern) throws IOException {
                pattern = standardize(pattern);
                if (!pattern.contains(REVISION_TOKEN)) {
                    LOGGER.debug("revision token not defined in pattern {}.", pattern);
                    return Collections.emptyList();
                }
                String prefix = pattern.substring(0, pattern.indexOf(REVISION_TOKEN));
                if (revisionMatchesDirectoryName(pattern)) {
                    return listAll(prefix);
                } else {
                    int parentFolderSlashIndex = prefix.lastIndexOf(fileSeparator);
                    String revisionParentFolder = parentFolderSlashIndex == -1 ? "" : prefix.substring(0, parentFolderSlashIndex + 1);
                    LOGGER.debug("using {} to list all in {} ", repository, revisionParentFolder);
                    if (!directories.add(revisionParentFolder)) {
                        return Collections.emptyList();
                    }
                    List<String> all = repository.list(revisionParentFolder);
                    if (all == null) {
                        return Collections.emptyList();
                    }
                    LOGGER.debug("found {} urls", all.size());
                    Pattern regexPattern = createRegexPattern(pattern, parentFolderSlashIndex);
                    List<String> ret = filterMatchedValues(all, regexPattern);
                    LOGGER.debug("{} matched {}" + pattern, ret.size(), pattern);
                    return ret;
                }
            }

            private String standardize(String source) {
                return source.replace('\\', '/');
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
                int endNameIndex = pattern.indexOf(fileSeparator, prefixLastSlashIndex + 1);
                String namePattern;
                if (endNameIndex != -1) {
                    namePattern = pattern.substring(prefixLastSlashIndex + 1, endNameIndex);
                } else {
                    namePattern = pattern.substring(prefixLastSlashIndex + 1);
                }
                namePattern = namePattern.replaceAll("\\.", "\\\\.");

                String acceptNamePattern = ".*?"
                        + namePattern.replaceAll("\\[revision\\]", "([^" + fileSeparator + "]+)")
                        + "($|" + fileSeparator + ".*)";

                return Pattern.compile(acceptNamePattern);
            }

            private boolean revisionMatchesDirectoryName(String pattern) {
                int startToken = pattern.indexOf(REVISION_TOKEN);
                if (startToken > 0 && !pattern.substring(startToken - 1, startToken).equals(fileSeparator)) {
                    // previous character is not a separator
                    return false;
                }
                int endToken = startToken + REV_TOKEN_LENGTH;
                if (endToken < pattern.length() && !pattern.substring(endToken, endToken + 1).equals(fileSeparator)) {
                    // next character is not a separator
                    return false;
                }
                return true;
            }

            private List<String> listAll(String parent) throws IOException {
                if (!directories.add(parent)) {
                    return Collections.emptyList();
                }
                LOGGER.debug("using {} to list all in {}", repository, parent);
                List<String> fullPaths = repository.list(parent);
                if (fullPaths == null) {
                    return Collections.emptyList();
                }
                LOGGER.debug("found {} resources", fullPaths.size());
                return extractVersionInfoFromPaths(fullPaths);
            }

            private List<String> extractVersionInfoFromPaths(List<String> paths) {
                List<String> ret = new ArrayList<String>(paths.size());
                for (String fullpath : paths) {
                    if (fullpath.endsWith(fileSeparator)) {
                        fullpath = fullpath.substring(0, fullpath.length() - 1);
                    }
                    int slashIndex = fullpath.lastIndexOf(fileSeparator);
                    ret.add(fullpath.substring(slashIndex + 1));
                }
                return ret;
            }
        };
    }
}
