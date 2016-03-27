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
import org.gradle.tooling.connection.BuildIdentity;
import org.gradle.tooling.connection.ProjectIdentity;
import org.gradle.tooling.internal.adapter.MethodInvocation;
import org.gradle.tooling.internal.adapter.MethodInvoker;
import org.gradle.tooling.internal.adapter.SourceObjectMapping;
import org.gradle.tooling.internal.protocol.DefaultBuildIdentity;
import org.gradle.tooling.internal.protocol.DefaultProjectIdentity;

import java.io.File;
import java.io.Serializable;

public class FixedBuildIdentifierProvider implements MethodInvoker, Serializable, Action<SourceObjectMapping> {
    private final BuildIdentity buildIdentity;
    private final ProjectIdentity projectIdentity;

    public FixedBuildIdentifierProvider(File rootDir, String projectPath) {
        this.buildIdentity = new DefaultBuildIdentity(rootDir);
        this.projectIdentity = new DefaultProjectIdentity(buildIdentity, projectPath);
    }

    public void invoke(MethodInvocation invocation) throws Throwable {
        if (BuildIdentity.class.isAssignableFrom(invocation.getReturnType())) {
            invocation.setResult(buildIdentity);
        }
        if (ProjectIdentity.class.isAssignableFrom(invocation.getReturnType())) {
            invocation.setResult(projectIdentity);
        }
    }

    @Override
    public void execute(SourceObjectMapping sourceObjectMapping) {
        sourceObjectMapping.mixIn(this);
    }
}
