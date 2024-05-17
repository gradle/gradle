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

import org.gradle.api.Project;

/**
 * A {@link ToolingModelBuilder} which can be parameterized by the client.
 *
 * <p>The parameter type {@code T} must be an interface and must follow a contract described in {@link #getParameterType()}.
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
 * @param <T> The type of parameter used by this model builder.
 * @see ToolingModelBuilder
 * @since 4.4
 */
public interface ParameterizedToolingModelBuilder<T> extends ToolingModelBuilder {

    /**
     * Returns the expected type of the parameter.
     * <p>
     * <b>Contract</b>
     * <p>
     * The parameter type must conform to the following rules:
     * <ul>
     *     <li>It must be an interface.</li>
     *     <li>It must contain only getter-like and setter-like methods.</li>
     *     <li>There must be one getter corresponding to exactly one setter, with matching value types.</li>
     * </ul>
     * <p>
     * For each getter-like method:
     * <ul>
     *     <li>The name must start with {@code "get"} or {@code "is"}, followed by an uppercase letter.</li>
     *     <li>It must have no parameters.</li>
     *     <li>The return type must not be {@code void}.</li>
     * </ul>
     * <p>
     * For each setter-like method:
     * <ul>
     *     <li>The name must start with {@code "set"}, followed by an uppercase letter.</li>
     *     <li>It must have exactly one parameter.</li>
     *     <li>The return type must be {@code void}.</li>
     * </ul>
     * <p>
     * <b>Example</b>
     * <p>
     * Here is an example of a valid parameter type declaration:
     * <pre>{@code
     * public interface MyParameter {
     *     String getValue();
     *     void setValue(String value);
     *
     *     boolean isFlag();
     *     void setFlag(boolean value);
     * }
     * }</pre>
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
