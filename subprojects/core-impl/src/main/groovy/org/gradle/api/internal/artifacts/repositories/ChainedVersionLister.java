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

import java.util.ArrayList;
import java.util.List;

/**
 * Created with IntelliJ IDEA. User: Rene Date: 23.07.12 Time: 15:00 To change this template use File | Settings | File Templates.
 */
public class ChainedVersionLister implements VersionLister {

    private List<VersionLister> versionListers = new ArrayList<VersionLister>();
    private boolean stopOnSuccess;

    public ChainedVersionLister(boolean stopOnSuccess) {
        this.stopOnSuccess = stopOnSuccess;
    }

    public VersionList getVersionList(ModuleRevisionId moduleRevisionId, String pattern, Artifact artifact) {
        ComposableVersionList composableVersionList = new ComposableVersionList();
        for (VersionLister versionLister : versionListers) {
            VersionList versionList = versionLister.getVersionList(moduleRevisionId, pattern, artifact);
            if (versionList.isEmpty()) {
                continue;
            }
            if (stopOnSuccess) {
                return versionList;
            } else {
                composableVersionList.addList(versionList);
            }
        }
        return composableVersionList;
    }

    public void appendVersionLister(VersionLister versionLister) {
        this.versionListers.add(versionLister);
    }
}
