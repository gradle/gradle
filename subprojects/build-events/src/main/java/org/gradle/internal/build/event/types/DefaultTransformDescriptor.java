/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.build.event.types;

import org.gradle.tooling.internal.protocol.events.InternalOperationDescriptor;
import org.gradle.tooling.internal.protocol.events.InternalTransformDescriptor;

import java.util.Set;

public class DefaultTransformDescriptor extends DefaultOperationDescriptor implements InternalTransformDescriptor {

    private final String transformerName;
    private final String subjectName;
    private final Set<InternalOperationDescriptor> dependencies;

    public DefaultTransformDescriptor(Object id, String displayName, Object parentId, String transformerName, String subjectName, Set<InternalOperationDescriptor> dependencies) {
        super(id, displayName, displayName, parentId);
        this.transformerName = transformerName;
        this.subjectName = subjectName;
        this.dependencies = dependencies;
    }

    @Override
    public String getTransformerName() {
        return transformerName;
    }

    @Override
    public String getSubjectName() {
        return subjectName;
    }

    @Override
    public Set<InternalOperationDescriptor> getDependencies() {
        return dependencies;
    }

}
