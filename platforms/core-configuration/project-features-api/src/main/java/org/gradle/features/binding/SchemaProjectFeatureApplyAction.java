/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.features.binding;

import org.gradle.api.Incubating;

/**
 * A {@link ProjectFeatureApplyAction} for project features whose definition has no
 * build model. Implementations provide build logic by overriding
 * {@link #apply(SchemaDefinition, Object)}, which receives only the definition and
 * its parent definition — no application context or build model.
 *
 * @param <OwnDefinition> the type of the project feature definition
 * @param <ParentDefinition> the type of the parent project feature definition
 *
 * @since 9.7.0
 */
@Incubating
public interface SchemaProjectFeatureApplyAction<OwnDefinition extends SchemaDefinition, ParentDefinition>
    extends ProjectFeatureApplyAction<OwnDefinition, BuildModel.None, ParentDefinition> {

    @Override
    default void apply(ProjectFeatureApplicationContext context, OwnDefinition definition, BuildModel.None buildModel, ParentDefinition parentDefinition) {
        apply(definition, parentDefinition);
    }

    /**
     * Apply build logic using only the project feature definition and its parent.
     *
     * @param definition the project feature definition
     * @param parentDefinition the parent project feature definition
     *
     * @since 9.7.0
     */
    void apply(OwnDefinition definition, ParentDefinition parentDefinition);
}
