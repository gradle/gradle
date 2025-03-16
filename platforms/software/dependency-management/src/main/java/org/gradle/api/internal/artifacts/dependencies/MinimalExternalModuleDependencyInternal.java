/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.internal.artifacts.dependencies;

import org.gradle.api.artifacts.MinimalExternalModuleDependency;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.attributes.AttributesFactory;
import org.gradle.api.model.ObjectFactory;
import org.gradle.internal.typeconversion.NotationParser;

public interface MinimalExternalModuleDependencyInternal extends MinimalExternalModuleDependency {
    void copyTo(AbstractExternalModuleDependency target);

    AttributesFactory getAttributesFactory();

    NotationParser<Object, Capability> getCapabilityNotationParser();

    ObjectFactory getObjectFactory();
}
