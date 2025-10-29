/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.plugins.java;

import org.gradle.api.artifacts.dsl.Dependencies;
import org.gradle.api.artifacts.dsl.DependencyCollector;
import org.gradle.declarative.dsl.model.annotations.Restricted;

@SuppressWarnings("UnstableApiUsage")
@Restricted
public interface LibraryDependencies extends Dependencies {
    DependencyCollector getApi();

    DependencyCollector getImplementation();

    DependencyCollector getRuntimeOnly();

    DependencyCollector getCompileOnly();

    default void copyTo(LibraryDependencies target) {
        this.getApi().getDependencies().get().forEach(target.getApi()::add);
        this.getApi().getDependencyConstraints().get().forEach(target.getApi()::addConstraint);

        this.getImplementation().getDependencies().get().forEach(target.getImplementation()::add);
        this.getImplementation().getDependencyConstraints().get().forEach(target.getImplementation()::addConstraint);

        this.getCompileOnly().getDependencies().get().forEach(target.getCompileOnly()::add);
        this.getCompileOnly().getDependencyConstraints().get().forEach(target.getCompileOnly()::addConstraint);

        this.getRuntimeOnly().getDependencies().get().forEach(target.getRuntimeOnly()::add);
        this.getRuntimeOnly().getDependencyConstraints().get().forEach(target.getRuntimeOnly()::addConstraint);
    }
}
