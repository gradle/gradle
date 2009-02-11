/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.dependencies;

import java.util.List;

/**
 * <p>An {@code Artifact} represents an artifact included in a {@link org.gradle.api.dependencies.Dependency}.</p>
 *
 * @author Hans Dockter
 */
public interface DependencyArtifact {
    String DEFAULT_TYPE = "jar";

    String getName();

    void setName(String name);

    String getType();

    void setType(String type);

    String getExtension();

    void setExtension(String extension);

    String getClassifier();

    void setClassifier(String classifier);

    String getUrl();

    void setUrl(String url);

    List<String> getConfs();

    void setConfs(List<String> confs);
}
