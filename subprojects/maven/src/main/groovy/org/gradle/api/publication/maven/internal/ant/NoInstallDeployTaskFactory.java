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

package org.gradle.api.publication.maven.internal.ant;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.DefaultArtifactRepository;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.gradle.internal.Factory;

import java.io.File;

public class NoInstallDeployTaskFactory implements Factory<CustomDeployTask> {
    private final Factory<File> temporaryDirFactory;

    public NoInstallDeployTaskFactory(Factory<File> temporaryDirFactory) {
        this.temporaryDirFactory = temporaryDirFactory;
    }

    public CustomDeployTask create() {
        return new NoInstallDeployTask(temporaryDirFactory);
    }

    private static class NoInstallDeployTask extends CustomDeployTask {
        private final Factory<File> tmpDirFactory;

        public NoInstallDeployTask(Factory<File> tmpDirFactory) {
            this.tmpDirFactory = tmpDirFactory;
        }

        @Override
        protected ArtifactRepository createLocalArtifactRepository() {
            ArtifactRepositoryLayout repositoryLayout = (ArtifactRepositoryLayout) lookup(ArtifactRepositoryLayout.ROLE, getLocalRepository().getLayout());
            return new DefaultArtifactRepository("local", tmpDirFactory.create().toURI().toString(), repositoryLayout);
        }
    }
}
