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

import org.gradle.api.artifacts.FileCollectionDependency;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.jspecify.annotations.Nullable;

public class DefaultFileCollectionDependency implements SelfResolvingDependencyInternal, FileCollectionDependency {

    private @Nullable String reason;
    private final @Nullable ComponentIdentifier targetComponentId;
    private final FileCollectionInternal source;

    public DefaultFileCollectionDependency(FileCollectionInternal source) {
        this.targetComponentId = null;
        this.source = source;
    }

    public DefaultFileCollectionDependency(ComponentIdentifier targetComponentId, FileCollectionInternal source) {
        this.targetComponentId = targetComponentId;
        this.source = source;
    }

    @Nullable
    @Override
    public String getReason() {
        return reason;
    }

    @Override
    public void because(@Nullable String reason) {
        this.reason = reason;
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
    public FileCollection getFiles() {
        return source;
    }

}
