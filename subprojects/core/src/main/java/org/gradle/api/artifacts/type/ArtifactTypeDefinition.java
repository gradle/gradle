/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.artifacts.type;

import org.gradle.api.Incubating;
import org.gradle.api.Named;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.HasAttributes;

import java.util.Set;

/**
 * Meta-data about a particular type of artifacts.
 *
 * @since 4.0
 */
@Incubating
public interface ArtifactTypeDefinition extends HasAttributes, Named {
    /**
     * Returns the set of file name extensions that should be mapped to this artifact type. Defaults to the name of this type.
     */
    Set<String> getFileNameExtensions();

    /**
     * Defines the set of attributes to apply to a component that is packaged as an artifact of this type, when no other attributes are defined. For example, these attributes are applied when a Maven module contains an artifact with one of the extensions listed in {@link #getFileNameExtensions()}.
     */
    @Override
    AttributeContainer getAttributes();
}
