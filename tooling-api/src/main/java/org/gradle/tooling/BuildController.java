/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.tooling;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.tooling.model.Model;
import org.gradle.tooling.model.gradle.GradleBuild;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;

/**
 * Provides a {@link BuildAction} various ways to control a Gradle build and access information about the build.
 *
 * @since 1.8
 */
public interface BuildController {
    /**
     * Fetches a snapshot of the model of the given type for the default project. The default project is generally the
     * project referenced when a {@link ProjectConnection} is created.
     *
     * <p>Any of following models types may be available, depending on the version of Gradle being used by the target
     * build:
     *
     * <ul>
     *     <li>{@link GradleBuild}</li>
     *     <li>{@link org.gradle.tooling.model.build.BuildEnvironment}</li>
     *     <li>{@link org.gradle.tooling.model.GradleProject}</li>
     *     <li>{@link org.gradle.tooling.model.gradle.BuildInvocations}</li>
     *     <li>{@link org.gradle.tooling.model.gradle.ProjectPublications}</li>
     *     <li>{@link org.gradle.tooling.model.idea.IdeaProject}</li>
     *     <li>{@link org.gradle.tooling.model.idea.BasicIdeaProject}</li>
     *     <li>{@link org.gradle.tooling.model.eclipse.EclipseProject}</li>
     *     <li>{@link org.gradle.tooling.model.eclipse.HierarchicalEclipseProject}</li>
     * </ul>
     *
     * <p>A build may also expose additional custom tooling models. You can use this method to query these models.
     *
     * @param modelType The model type.
     * @param <T> The model type.
     * @return The model.
     * @throws UnknownModelException When the default project does not support the requested model.
     * @since 1.8
     */
    <T> T getModel(Class<T> modelType) throws UnknownModelException;

    /**
     * Fetches a snapshot of the model of the given type, if available.
     *
     * <p>See {@link #getModel(Class)} for more details.</p>
     *
     * @param modelType The model type.
     * @param <T> The model type.
     * @return The model, or null if not present.
     */
    @Nullable
    <T> T findModel(Class<T> modelType);

    /**
     * Returns an overview of the Gradle build, including some basic details of the projects that make up the build.
     * This is equivalent to calling {@code #getModel(GradleBuild.class)}.
     *
     * @return The model.
     */
    GradleBuild getBuildModel();

    /**
     * Fetches a snapshot of the model of the given type for the given element, usually a Gradle project.
     *
     * <p>The following elements are supported:
     *
     * <ul>
     *     <li>Any {@link org.gradle.tooling.model.gradle.BasicGradleProject}</li>
     *     <li>Any {@link org.gradle.tooling.model.GradleProject}</li>
     *     <li>Any {@link org.gradle.tooling.model.eclipse.EclipseProject}</li>
     *     <li>Any {@link org.gradle.tooling.model.idea.IdeaModule}</li>
     * </ul>
     *
     * <p>See {@link #getModel(Class)} for more details.
     *
     * @param target The target element, usually a project.
     * @param modelType The model type.
     * @param <T> The model type.
     * @return The model.
     * @throws UnknownModelException When the target project does not support the requested model.
     */
    <T> T getModel(Model target, Class<T> modelType) throws UnknownModelException;

    /**
     * Fetches a snapshot of the model of the given type, if available.
     *
     * <p>See {@link #getModel(Model, Class)} for more details.</p>
     *
     * @param modelType The model type.
     * @param <T> The model type.
     * @return The model, or null if not present.
     */
    @Nullable
    <T> T findModel(Model target, Class<T> modelType);

    /**
     * Fetches a snapshot of the model of the given type using the given parameter.
     *
     * <p>See {@link #getModel(Model, Class, Class, Action)} for more details.
     *
     * @param modelType The model type.
     * @param <T> The model type.
     * @param parameterType The parameter type.
     * @param <P> The parameter type.
     * @param parameterInitializer Action to configure the parameter
     * @return The model.
     * @throws UnknownModelException When the target project does not support the requested model.
     * @throws UnsupportedVersionException When the target project does not support the requested model or Gradle version does not support parameterized models.
     * @since 4.4
     */
    <T, P> T getModel(Class<T> modelType, Class<P> parameterType, Action<? super P> parameterInitializer) throws UnsupportedVersionException, UnknownModelException;

    /**
     * Fetches a snapshot of the model of the given type using the given parameter, if available.
     *
     * <p>See {@link #getModel(Model, Class, Class, Action)} for more details.
     *
     * @param modelType The model type.
     * @param <T> The model type.
     * @param parameterType The parameter type.
     * @param <P> The parameter type.
     * @param parameterInitializer Action to configure the parameter
     * @return The model.
     * @since 4.4
     */
    @Nullable
    <T, P> T findModel(Class<T> modelType, Class<P> parameterType, Action<? super P> parameterInitializer);

    /**
     * Fetches a snapshot of the model of the given type for the given element using the given parameter.
     *
     * <p>The parameter type must be an interface only with getters and setters and no nesting is supported.
     * The Tooling API will create a proxy instance of this interface and use the initializer to run against
     * that instance to configure it and then pass to the model builder.
     * </p>
     *
     * <p>See {@link #getModel(Class)} for more details.
     *
     * @param target The target element, usually a project.
     * @param modelType The model type.
     * @param <T> The model type.
     * @param parameterType The parameter type.
     * @param <P> The parameter type.
     * @param parameterInitializer Action to configure the parameter
     * @return The model.
     * @throws UnknownModelException When the target project does not support the requested model.
     * @throws UnsupportedVersionException When the target project does not support the requested model or Gradle version does not support parameterized models.
     * @since 4.4
     */
    <T, P> T getModel(Model target, Class<T> modelType, Class<P> parameterType, Action<? super P> parameterInitializer) throws UnsupportedVersionException, UnknownModelException;

    /**
     * Fetches a snapshot of the model of the given type for the given element using the given parameter, if available.
     *
     * <p>See {@link #getModel(Model, Class, Class, Action)} for more details.
     *
     * @param target The target element, usually a project.
     * @param modelType The model type.
     * @param <T> The model type.
     * @param parameterType The parameter type.
     * @param <P> The parameter type.
     * @param parameterInitializer Action to configure the parameter
     * @return The model.
     * @since 4.4
     */
    @Nullable
    <T, P> T findModel(Model target, Class<T> modelType, Class<P> parameterType, Action<? super P> parameterInitializer);

    /**
     * Runs the given actions and returns their results. Attempts to run the actions in parallel, when supported by the Gradle version.
     *
     * <p>This method works with all Gradle versions. For versions 6.7 and earlier, the actions are run sequentially rather than in parallel.</p>
     *
     * <p>When one or more actions fail with an exception, the exceptions are rethrown by this method and no result is returned.</p>
     *
     * @param actions The actions to run.
     * @param <T> the result type.
     * @return The action results. These are returned in the same order as the actions that produce them.
     * @since 6.8
     */
    @Incubating
    <T> List<T> run(Collection<? extends BuildAction<? extends T>> actions);

    /**
     * Returns {@code true} when actions run using {@link #run(Collection)} and that query project models of the given type will run in parallel.
     * Returns {@code false} when the models will not be queried in parallel, for example because the target Gradle version does not support parallel execution,
     * or because some build configuration disables the parallel execution, or because the queries are unsafe to perform in parallel.
     *
     * @return {@code true} when project models may be queried in parallel.
     * @since 6.8
     */
    @Incubating
    boolean getCanQueryProjectModelInParallel(Class<?> modelType);
}
