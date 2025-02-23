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

package org.gradle.api.isolated.models;

import org.gradle.api.Incubating;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import java.util.Collection;

/**
 * TBD
 *
 * @since 8.13
 */
@ServiceScope(Scope.Project.class)
@Incubating
public interface ProjectModelScope {

    // TODO: plain Provider may be too loose of a type to express a model producer
    //  It is likely to be supplemented with runtime checks to validate which providers are supported
    //  We need to consider stricter type-driven definitions of model producers

    /**
     * TBD
     *
     * @since 8.13
     */
    <T> void register(Class<T> modelType, Provider<T> modelProducer);

    /**
     * TBD
     *
     * @since 8.13
     */
    <T> ProjectScopeModelRequest<T> request(Class<T> modelType, Collection<Project> projects);

}
