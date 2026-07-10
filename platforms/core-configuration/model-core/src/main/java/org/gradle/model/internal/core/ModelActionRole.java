/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.model.internal.core;

/**
 * A hard-coded sequence of model actions that can be applied to a model element.
 *
 * <p>This is pretty much a placeholder for something more descriptive.
 */
public enum ModelActionRole {
    Discover(ModelNode.State.Discovered, false), // Defines all projections for the node
    Create(ModelNode.State.Created, false), // Initializes the node
    Defaults(ModelNode.State.DefaultsApplied, true), // Allows a mutation to setup default values for an element
    Initialize(ModelNode.State.Initialized, true), // Mutation action provided when an element is defined
    Mutate(ModelNode.State.Mutated, true), // Customisations
    Finalize(ModelNode.State.Finalized, true), // Post customisation default values
    Validate(ModelNode.State.SelfClosed, true); // Post mutation validations

    private final ModelNode.State target;
    private final boolean subjectViewAvailable;

    ModelActionRole(ModelNode.State target, boolean subjectViewAvailable) {
        this.target = target;
        this.subjectViewAvailable = subjectViewAvailable;
    }

    public ModelNode.State getTargetState() {
        return target;
    }

    /**
     * Returns whether the private data of the subject node can be viewed as a Java object by a rule in this role.
     */
    public boolean isSubjectViewAvailable() {
        return subjectViewAvailable;
    }
}
