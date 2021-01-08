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

package org.gradle.tooling.internal.consumer.converters;

import org.gradle.tooling.internal.gradle.DefaultBuildIdentifier;
import org.gradle.tooling.internal.gradle.DefaultProjectIdentifier;
import org.gradle.tooling.model.gradle.BasicGradleProject;

import java.io.Serializable;

public class BasicGradleProjectIdentifierMixin implements Serializable {
    private final DefaultBuildIdentifier buildIdentifier;

    public BasicGradleProjectIdentifierMixin(DefaultBuildIdentifier buildIdentifier) {
        this.buildIdentifier = buildIdentifier;
    }

    public DefaultProjectIdentifier getProjectIdentifier(BasicGradleProject gradleProject) {
        return new DefaultProjectIdentifier(buildIdentifier, gradleProject.getPath());
    }
}
