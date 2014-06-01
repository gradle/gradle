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

import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ResourceAwareResolveResult;
import org.gradle.api.internal.artifacts.metadata.IvyArtifactName;
import org.gradle.internal.resource.ResourceException;
import org.gradle.internal.resource.ResourceNotFoundException;
import org.gradle.util.DeprecationLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class ChainedVersionLister implements VersionLister {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExternalResourceResolver.class);
    private final List<VersionLister> versionListers;

    public ChainedVersionLister(VersionLister... delegates) {
        this.versionListers = Arrays.asList(delegates);
    }

    public VersionList getVersionList(final ModuleIdentifier module, final Collection<String> dest, final ResourceAwareResolveResult result)  {
        final List<VersionList> versionLists = new ArrayList<VersionList>();
        for (VersionLister lister : versionListers) {
            versionLists.add(lister.getVersionList(module, dest, result));
        }
        return new VersionList() {
            public void visit(ResourcePattern pattern, IvyArtifactName artifact) throws ResourceException {
                final Iterator<VersionList> versionListIterator = versionLists.iterator();
                while (versionListIterator.hasNext()) {
                    VersionList list = versionListIterator.next();
                    try {
                        list.visit(pattern, artifact);
                        return;
                    } catch (ResourceNotFoundException e) {
                        if (!versionListIterator.hasNext()) {
                            throw e;
                        }
                    } catch (Exception e) {
                        if (versionListIterator.hasNext()) {
                            String deprecationMessage = String.format(
                                    "Error listing versions of %s using %s. Will attempt an alternate way to list versions",
                                    module, list.getClass()
                            );
                            DeprecationLogger.nagUserOfDeprecatedBehaviour(deprecationMessage);
                            LOGGER.debug(deprecationMessage, e);
                        } else {
                            throw new ResourceException(String.format("Failed to list versions for %s.", module), e);
                        }
                    }
                }
            }
        };
    }
}
