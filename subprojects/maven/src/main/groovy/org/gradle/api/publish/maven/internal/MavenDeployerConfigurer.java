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

package org.gradle.api.publish.maven.internal;

import org.apache.maven.artifact.ant.Authentication;
import org.apache.maven.artifact.ant.RemoteRepository;
import org.gradle.api.Action;
import org.gradle.api.artifacts.maven.MavenDeployer;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.artifacts.repositories.PasswordCredentials;

public class MavenDeployerConfigurer implements Action<MavenDeployer> {

    private final MavenArtifactRepository artifactRepository;

    public MavenDeployerConfigurer(MavenArtifactRepository artifactRepository) {
        this.artifactRepository = artifactRepository;
    }

    public void execute(MavenDeployer deployer) {
        deployer.setRepository(createRepository());
    }

    private RemoteRepository createRepository() {
        RemoteRepository remoteRepository = new RemoteRepository();
        remoteRepository.setUrl(artifactRepository.getUrl().toString());

        PasswordCredentials credentials = artifactRepository.getCredentials();
        String username = credentials.getUsername();
        String password = credentials.getPassword();

        if (username != null || password != null) {
            Authentication authentication = new Authentication();
            authentication.setUserName(username);
            authentication.setPassword(password);
            remoteRepository.addAuthentication(authentication);
        }

        return remoteRepository;
    }
}
