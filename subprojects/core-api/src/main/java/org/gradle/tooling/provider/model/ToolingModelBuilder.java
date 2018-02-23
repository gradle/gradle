/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.tooling.provider.model;

import org.gradle.api.Incubating;
import org.gradle.api.Project;

/**
 * Responsible for building tooling models to return to the tooling API client.
 *
 * <p>The {@link #buildAll(String, Project)} method is called to create a model for a given project. The model is serialized to the client process and passed
 * to the client application.</p>
 *
 * <p>The model object is adapted to the Java type that is used by the client by generating a view, or wrapper object, over the model object.
 * The model object does not need to implement the client Java type, but it does need to have the same structure as the client type.
 * This means that the model object should have the same properties and methods as those defined on the client type. The tooling API deals with
 * missing properties and methods, to allow evolution of the models. It will also adapt the values returned by the methods of the model object to the
 * types used by the client.
 * </p>
 *
 * <p>Although it is not enforced, the model object should be immutable, as the tooling API will do some caching and other performance optimizations on the
 * assumption that the model is effectively immutable. The tooling API does not make any guarantees about how the client application will use the model object.</p>
 */
@Incubating
public interface ToolingModelBuilder {
    /**
     * Indicates whether this builder can construct the given model.
     *
     * @param modelName The model name, usually the same as the name of the Java interface used by the client.
     * @return true if this builder can construct the model, false if not.
     */
    boolean canBuild(String modelName);

    /**
     * Creates the model of the given type for the given project.
     *
     * @param modelName The model name, usually the same as the name of the Java interface used by the client.
     * @param project The project to create the model for.
     * @return The model.
     */
    Object buildAll(String modelName, Project project);
}
