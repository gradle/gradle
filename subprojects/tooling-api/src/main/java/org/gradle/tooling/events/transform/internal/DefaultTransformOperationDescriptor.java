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

package org.gradle.tooling.events.transform.internal;

import org.gradle.tooling.events.OperationDescriptor;
import org.gradle.tooling.events.internal.DefaultOperationDescriptor;
import org.gradle.tooling.events.transform.TransformOperationDescriptor;
import org.gradle.tooling.internal.protocol.events.InternalTransformDescriptor;

import java.util.Set;

public class DefaultTransformOperationDescriptor extends DefaultOperationDescriptor implements TransformOperationDescriptor {

    private final TransformerDescriptor transformer;
    private final SubjectDescriptor subject;
    private final Set<OperationDescriptor> dependencies;

    public DefaultTransformOperationDescriptor(InternalTransformDescriptor descriptor, OperationDescriptor parent, Set<OperationDescriptor> dependencies) {
        super(descriptor, parent);
        this.transformer = new DefaultTransformerDescriptor(descriptor.getTransformerName());
        this.subject = new DefaultSubjectDescriptor(descriptor.getSubjectName());
        this.dependencies = dependencies;
    }

    @Override
    public TransformerDescriptor getTransformer() {
        return transformer;
    }

    @Override
    public SubjectDescriptor getSubject() {
        return subject;
    }

    @Override
    public Set<? extends OperationDescriptor> getDependencies() {
        return dependencies;
    }

    private static class DefaultTransformerDescriptor implements TransformerDescriptor {

        private final String displayName;

        public DefaultTransformerDescriptor(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String getDisplayName() {
            return displayName;
        }

    }

    private static class DefaultSubjectDescriptor implements SubjectDescriptor {

        private final String displayName;

        public DefaultSubjectDescriptor(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String getDisplayName() {
            return displayName;
        }

    }

}
