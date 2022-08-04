/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.publish.ivy.internal.artifact;

import org.gradle.api.publish.internal.PublicationInternal;
import org.gradle.api.publish.ivy.IvyArtifact;
import org.gradle.api.tasks.TaskDependency;

import java.io.File;

import static com.google.common.io.Files.getFileExtension;

public class DerivedIvyArtifact extends AbstractIvyArtifact {
    private final IvyArtifact original;
    private final PublicationInternal.DerivedArtifact derived;

    public DerivedIvyArtifact(IvyArtifact original, PublicationInternal.DerivedArtifact derived) {
        this.original = original;
        this.derived = derived;
    }

    @Override
    protected String getDefaultName() {
        return original.getName();
    }

    @Override
    protected String getDefaultType() {
        return getFileExtension(getFile().getName());
    }

    @Override
    protected String getDefaultExtension() {
        return original.getExtension() + "." + getType();
    }

    @Override
    protected String getDefaultClassifier() {
        return original.getClassifier();
    }

    @Override
    protected String getDefaultConf() {
        return original.getConf();
    }

    @Override
    protected TaskDependency getDefaultBuildDependencies() {
        return original.getBuildDependencies();
    }

    @Override
    public File getFile() {
        return derived.create();
    }

    public boolean shouldBePublished() {
        return derived.shouldBePublished();
    }
}
