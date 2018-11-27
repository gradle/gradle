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

package org.gradle.api.internal.artifacts.dsl.dependencies;

import org.gradle.api.UnknownProjectException;
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.internal.project.ProjectInternal;

import javax.annotation.Nullable;

public class UnknownProjectFinder implements ProjectFinder {
    private final String exceptionMessage;

    public UnknownProjectFinder(String exceptionMessage) {
        this.exceptionMessage = exceptionMessage;
    }

    @Override
    public ProjectInternal findProject(String path) {
        return null;
    }

    @Nullable
    @Override
    public ProjectInternal findProject(BuildIdentifier build, String path) {
        return null;
    }

    @Override
    public ProjectInternal getProject(String path) {
        throw new UnknownProjectException(exceptionMessage);
    }
}
