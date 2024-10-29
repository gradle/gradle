/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.service.scopes;

import org.gradle.api.internal.artifacts.Module;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;

/**
 * Represents the root component identity of a detached configuration.
 * <p>
 * <strong>The goal is to make the following statements true by adding a "RootComponentIdentifier",
 * however this was not done to avoid introducing further complexity during a regression fix.</strong>
 * <p>
 * The root component of a detached configuration is adhoc and contains only that configuration.
 * For this reason, the root component of the detached configuration cannot declare the same
 * module coordinates as the project it was derived from, otherwise the resolution engine will
 * consider the detached root component an instance of that module.
 * <p>
 * The detached root component created from a given project does not advertise the variants
 * of that project and thus must have different coordinates.
 */
public class DetachedDependencyMetadataProvider implements DependencyMetaDataProvider {

    private final DependencyMetaDataProvider delegate;

    public DetachedDependencyMetadataProvider(DependencyMetaDataProvider delegate) {
        this.delegate = delegate;
    }

    @Override
    public Module getModule() {
        return delegate.getModule();
    }

 }
