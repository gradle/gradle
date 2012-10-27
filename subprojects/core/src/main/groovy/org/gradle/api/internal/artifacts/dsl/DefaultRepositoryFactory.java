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

package org.gradle.api.internal.artifacts.dsl;

import org.gradle.api.Action;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.artifacts.repositories.FlatDirectoryArtifactRepository;
import org.gradle.api.artifacts.repositories.IvyArtifactRepository;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.internal.artifacts.BaseRepositoryFactory;
import org.gradle.util.ConfigureUtil;
import org.gradle.util.DeprecationLogger;
import org.gradle.util.GUtil;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.gradle.api.artifacts.ArtifactRepositoryContainer.DEFAULT_MAVEN_CENTRAL_REPO_NAME;
import static org.gradle.api.artifacts.ArtifactRepositoryContainer.DEFAULT_MAVEN_LOCAL_REPO_NAME;
import static org.gradle.util.CollectionUtils.toList;

public class DefaultRepositoryFactory implements RepositoryFactoryInternal {

    public static final String FLAT_DIR_DEFAULT_NAME = "flatDir";
    private static final String MAVEN_REPO_DEFAULT_NAME = "maven";
    private static final String IVY_REPO_DEFAULT_NAME = "ivy";

    private final BaseRepositoryFactory baseRepositoryFactory;

    public DefaultRepositoryFactory(BaseRepositoryFactory baseRepositoryFactory) {
        this.baseRepositoryFactory = baseRepositoryFactory;
    }

    public BaseRepositoryFactory getBaseRepositoryFactory() {
        return baseRepositoryFactory;
    }

    public FlatDirectoryArtifactRepository flatDir(Action<? super FlatDirectoryArtifactRepository> action) {
        return configure(getBaseRepositoryFactory().createFlatDirRepository(), action, FLAT_DIR_DEFAULT_NAME);
    }

    public FlatDirectoryArtifactRepository flatDir(Map<String, ?> args) {
        Map<String, Object> modifiedArgs = new HashMap<String, Object>(args);
        if (modifiedArgs.containsKey("dirs")) {
            modifiedArgs.put("dirs", toList(modifiedArgs.get("dirs")));
        }

        return configure(getBaseRepositoryFactory().createFlatDirRepository(), modifiedArgs, FLAT_DIR_DEFAULT_NAME);
    }

    public MavenArtifactRepository mavenLocal() {
        return configure(baseRepositoryFactory.createMavenLocalRepository(), DEFAULT_MAVEN_LOCAL_REPO_NAME);
    }

    public MavenArtifactRepository mavenCentral() {
        return mavenCentral(Collections.<String, Object>emptyMap());
    }

    public MavenArtifactRepository mavenCentral(Map<String, ?> args) {
        Map<String, Object> modifiedArgs = new HashMap<String, Object>(args);
        if (modifiedArgs.containsKey("urls")) {
            DeprecationLogger.nagUserWith("The 'urls' property of the RepositoryHandler.mavenCentral() method is deprecated and will be removed in a future version of Gradle. "
                    + "You should use the 'artifactUrls' property to define additional artifact locations.");
            List<Object> urls = toList(modifiedArgs.remove("urls"));
            modifiedArgs.put("artifactUrls", urls);
        }

        return configure(baseRepositoryFactory.createMavenCentralRepository(), modifiedArgs, DEFAULT_MAVEN_CENTRAL_REPO_NAME);
    }

    public MavenArtifactRepository maven(Action<? super MavenArtifactRepository> action) {
        return configure(baseRepositoryFactory.createMavenRepository(), action, MAVEN_REPO_DEFAULT_NAME);
    }

    public IvyArtifactRepository ivy(Action<? super IvyArtifactRepository> action) {
        return configure(baseRepositoryFactory.createIvyRepository(), action, IVY_REPO_DEFAULT_NAME);
    }

    private <T extends ArtifactRepository> T configure(T repository, Action<? super T> action, String name) {
        action.execute(repository);
        return configure(repository, name);
    }

    private <T extends ArtifactRepository> T configure(T repository, Map<String, ?> properties, String name) {
        ConfigureUtil.configureByMap(properties, repository);
        return configure(repository, name);
    }

    private <T extends ArtifactRepository> T configure(T repository, String name) {
        String repositoryName = repository.getName();
        if (!GUtil.isTrue(repositoryName)) {
            repository.setName(name);
        }
        return repository;
    }

}
