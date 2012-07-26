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

import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.util.DeprecationLogger;

import java.util.*;

public class ChainedVersionLister implements VersionLister {

    private List<VersionLister> versionListers = new ArrayList<VersionLister>();

    public ChainedVersionLister(VersionLister... versionlisters) {
        this.versionListers = Arrays.asList(versionlisters);
    }

    public VersionList getVersionList(ModuleRevisionId moduleRevisionId, String pattern, Artifact artifact) throws ComposedResourceException {
        final Iterator<VersionLister> versionListerIterator = versionListers.iterator();

        while (versionListerIterator.hasNext()) {
            VersionLister lister = versionListerIterator.next();
            try {
                final VersionList versionList = lister.getVersionList(moduleRevisionId, pattern, artifact);
                if (!versionListerIterator.hasNext() || !versionList.isEmpty()) {
                    return versionList;
                }

            } catch (Exception e) {
                if (versionListerIterator.hasNext()) {
                    DeprecationLogger.nagUserWith(String.format("Failed to list versions of %s using %s. Proceeding with other ResourceLister.", moduleRevisionId, lister.getClass()));
                } else {
                    throw new ComposedResourceException(String.format("Failed to list versions for %s", moduleRevisionId, lister.getClass()), e);
                }
            }
        }
        return new DefaultVersionList(Collections.<String>emptyList());
    }
}
