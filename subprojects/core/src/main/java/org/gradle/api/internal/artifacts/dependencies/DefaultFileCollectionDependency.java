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
package org.gradle.api.internal.artifacts.dependencies;

import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.FileCollectionDependency;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.deprecation.DeprecationLogger;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Set;

public class DefaultFileCollectionDependency extends AbstractDependency implements SelfResolvingDependencyInternal, FileCollectionDependency {
    private final ComponentIdentifier targetComponentId;
    private final FileCollectionInternal source;

    public DefaultFileCollectionDependency(FileCollectionInternal source) {
        this.targetComponentId = null;
        this.source = source;
    }

    public DefaultFileCollectionDependency(ComponentIdentifier targetComponentId, FileCollectionInternal source) {
        this.targetComponentId = targetComponentId;
        this.source = source;
    }

    @Override
    @Deprecated
    public boolean contentEquals(Dependency dependency) {

        DeprecationLogger.deprecateMethod(Dependency.class, "contentEquals(Dependency)")
            .withAdvice("Use Object.equals(Object) instead")
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(8, "deprecated_content_equals")
            .nagUser();

        if (!(dependency instanceof DefaultFileCollectionDependency)) {
            return false;
        }
        DefaultFileCollectionDependency selfResolvingDependency = (DefaultFileCollectionDependency) dependency;
        return source.equals(selfResolvingDependency.source);
    }

    @Override
    public DefaultFileCollectionDependency copy() {
        return new DefaultFileCollectionDependency(targetComponentId, source);
    }

    @Nullable
    @Override
    public ComponentIdentifier getTargetComponentId() {
        return targetComponentId;
    }

    @Override
    public String getGroup() {
        return null;
    }

    @Override
    public String getName() {
        return "unspecified";
    }

    @Override
    public String getVersion() {
        return null;
    }

    @Override
    @Deprecated
    @SuppressWarnings("deprecation")
    public void resolve(org.gradle.api.internal.artifacts.CachingDependencyResolveContext context) {
        context.add(source);
    }

    @Override
    @Deprecated
    public Set<File> resolve() {
        DeprecationLogger.deprecate("Directly resolving a file collection dependency's files")
            .withAdvice("Add the dependency to a resolvable configuration and resolve the configuration.")
            .willBecomeAnErrorInGradle9()
            .withUpgradeGuideSection(8, "deprecate_self_resolving_dependency")
            .nagUser();

        return source.getFiles();
    }

    @Override
    @Deprecated
    public Set<File> resolve(boolean transitive) {
        DeprecationLogger.deprecate("Directly resolving a file collection dependency's files")
            .withAdvice("Add the dependency to a resolvable configuration and resolve the configuration.")
            .willBecomeAnErrorInGradle9()
            .withUpgradeGuideSection(8, "deprecate_self_resolving_dependency")
            .nagUser();

        return source.getFiles();
    }

    @Override
    @Deprecated
    public TaskDependency getBuildDependencies() {
        DeprecationLogger.deprecate("Accessing the build dependencies of a file collection dependency")
            .withAdvice("Add the dependency to a resolvable configuration use the configuration to track task dependencies.")
            .willBecomeAnErrorInGradle9()
            .withUpgradeGuideSection(8, "deprecate_self_resolving_dependency")
            .nagUser();

        return source.getBuildDependencies();
    }

    @Override
    public FileCollection getFiles() {
        return source;
    }

}
