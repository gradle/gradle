/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.data;

import org.gradle.api.artifacts.ModuleIdentifier;

import java.util.List;

public interface PomDependencyMgt {
    MavenDependencyKey getId();
    String getGroupId();
    String getArtifactId();
    String getVersion();
    String getScope();
    String getType();
    String getClassifier();
    List<ModuleIdentifier> getExcludedModules();
}
