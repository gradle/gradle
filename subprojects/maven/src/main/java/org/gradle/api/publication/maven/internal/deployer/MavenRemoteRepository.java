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

package org.gradle.api.publication.maven.internal.deployer;

import org.apache.maven.artifact.ant.Authentication;
import org.apache.maven.artifact.ant.Proxy;
import org.apache.maven.artifact.ant.RemoteRepository;
import org.apache.maven.artifact.ant.RepositoryPolicy;
import org.gradle.util.ConfigureUtil;

import java.util.Map;

public class MavenRemoteRepository extends RemoteRepository {
    public Authentication authentication(Map properties) {
        Authentication authentication = new Authentication();
        ConfigureUtil.configureByMap(properties, authentication);
        addAuthentication(authentication);
        return authentication;
    }

    public Proxy proxy(Map properties) {
        Proxy proxy = new Proxy();
        ConfigureUtil.configureByMap(properties, proxy);
        addProxy(proxy);
        return proxy;
    }

    public RepositoryPolicy releases(Map properties) {
        RepositoryPolicy policy = new RepositoryPolicy();
        ConfigureUtil.configureByMap(properties, policy);
        addReleases(policy);
        return policy;
    }

    public RepositoryPolicy snapshots(Map properties) {
        RepositoryPolicy policy = new RepositoryPolicy();
        ConfigureUtil.configureByMap(properties, policy);
        addSnapshots(policy);
        return policy;
    }
}
