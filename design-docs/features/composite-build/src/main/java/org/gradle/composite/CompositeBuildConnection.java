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

package org.gradle.composite;

import org.gradle.tooling.ModelBuilder;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.ResultHandler;

import java.util.List;
import java.util.Set;

/**
 * Connection to a Gradle build, whether that is a multi-project build or a composite build.
 * Provides access to models for all projects within that build.
 */
public interface CompositeBuildConnection {
    // This is all that's strictly required for Eclipse dependency substitution:
    <T> Set<ModelResult<T>> getModels(Class<T> modelType);

    // These are candidates for future usability of the composite connection
    List<ProjectIdentity> getProjects();

    // Model methods targeting a single project
    ProjectConnection project(ProjectIdentity id);
    <T> T getModel(ProjectIdentity id, Class<T> modelType);
    <T> void getModel(ProjectIdentity id, Class<T> modelType, ResultHandler<? super T> handler) throws IllegalStateException;
    <T> ModelBuilder<T> model(ProjectIdentity id, Class<T> modelType);

    // Model methods to get model for all projects
    <T> void getModels(Class<T> modelType, CompositeResultHandler<T> handler) throws IllegalStateException;
    <T> CompositeModelBuilder<T> models(Class<T> modelType);
}


