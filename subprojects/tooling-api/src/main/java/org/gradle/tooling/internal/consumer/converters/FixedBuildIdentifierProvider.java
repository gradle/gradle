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

import org.gradle.api.Action;
import org.gradle.tooling.model.BuildIdentifier;
import org.gradle.tooling.model.ProjectIdentifier;
import org.gradle.tooling.internal.adapter.MethodInvocation;
import org.gradle.tooling.internal.adapter.MethodInvoker;
import org.gradle.tooling.internal.adapter.SourceObjectMapping;

import java.io.Serializable;

public class FixedBuildIdentifierProvider implements MethodInvoker, Serializable, Action<SourceObjectMapping> {
    private final BuildIdentifier buildIdentifier;
    private final ProjectIdentifier projectIdentifier;

    public FixedBuildIdentifierProvider(ProjectIdentifier projectIdentifier) {
        this.buildIdentifier = projectIdentifier.getBuildIdentifier();
        this.projectIdentifier = projectIdentifier;
    }

    public void invoke(MethodInvocation invocation) throws Throwable {
        if (BuildIdentifier.class.isAssignableFrom(invocation.getReturnType())) {
            invocation.setResult(buildIdentifier);
        }
        if (ProjectIdentifier.class.isAssignableFrom(invocation.getReturnType())) {
            invocation.setResult(projectIdentifier);
        }
    }

    @Override
    public void execute(SourceObjectMapping sourceObjectMapping) {
        sourceObjectMapping.mixIn(this);
    }
}
