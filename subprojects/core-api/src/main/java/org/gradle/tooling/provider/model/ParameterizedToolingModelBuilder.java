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

package org.gradle.tooling.provider.model;

import org.gradle.api.Incubating;
import org.gradle.api.Project;

/**
 * Responsible for building parameterized tooling models to return to the tooling API client.
 *
 * <p>The {@link #buildAll(String, T, Project)} method is called to create a model for a given project using a given parameter.
 * The model is serialized to the client process and passed to the client application.</p>
 *
 * <p>The model object is adapted to the Java type that is used by the client by generating a view, or wrapper object, over the model object.
 * The model object does not need to implement the client Java type, but it does need to have the same structure as the client type.
 * This means that the model object should have the same properties and methods as those defined on the client type. The tooling API deals with
 * missing properties and methods, to allow evolution of the models. It will also adapt the values returned by the methods of the model object to the
 * types used by the client.
 * </p>
 *
 * <p>The parameter type {@code T} does not need to implement the interface defined in the client side, but it does need to have the same structure.
 * The Tooling API will create a view from the client side parameter type to the one defined in this model builder, and deal automatically with
 * missing methods in order to allow evolution of the models.
 * </p>
 *
 * <p>All classes implementing this interface should also implement methods from {@link ToolingModelBuilder}, which will be used to determine if
 * a model can be built by the current builder and to generate the model in case no parameter is passed from the client.
 * The parameter type should be bound to the model type.
 * </p>
 *
 * <p>Although it is not enforced, the model object should be immutable, as the tooling API will do some caching and other performance optimizations on the
 * assumption that the model is effectively immutable. The tooling API does not make any guarantees about how the client application will use the model object.</p>
 *
 * @param <T> The type of parameter used by this model builder.
 * @since 4.3
 * @see ToolingModelBuilder
 */
@Incubating
public interface ParameterizedToolingModelBuilder<T> extends ToolingModelBuilder {
    /**
     * Returns the expected type of the parameter.
     * It should be an interface only with getters and setters.
     *
     * @return The type of the parameter.
     */
    Class<T> getParameterType();

    /**
     * Creates the model of the given type for the given project using the given parameter.
     *
     * @param modelName The model name, usually the same as the name of the Java interface used by the client.
     * @param parameter The parameter received from the client.
     * @param project The project to create the model for.
     * @return The model.
     */
    Object buildAll(String modelName, T parameter, Project project);
}
