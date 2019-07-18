/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.artifacts.transform;

import org.gradle.api.Action;

import javax.inject.Inject;

/**
 * Interface for artifact transform actions.
 *
 * <p>
 *     A transform action implementation is an abstract class implementing the {@link #transform(TransformOutputs)} method.
 *     A minimal implementation may look like this:
 * </p>
 *
 * <pre class='autoTested'>
 * import org.gradle.api.artifacts.transform.TransformParameters;
 *
 * public abstract class MyTransform implements TransformAction&lt;TransformParameters.None&gt; {
 *     {@literal @}InputArtifact
 *     public abstract Provider&lt;FileSystemLocation&gt; getInputArtifact();
 *
 *     {@literal @}Override
 *     public void transform(TransformOutputs outputs) {
 *         File input = getInputArtifact().get().getAsFile();
 *         File output = outputs.file(input.getName() + ".transformed");
 *         // Do something to generate output from input
 *     }
 * }
 * </pre>
 *
 * Implementations of TransformAction are subject to the following constraints:
 * <ul>
 *     <li>Do not implement {@link #getParameters()} in your class, the method will be implemented by Gradle.</li>
 *     <li>Implementations may only have a default constructor.</li>
 * </ul>
 *
 *  Implementations can receive parameters by using annotated abstract getter methods.
 *  <ul>
 *      <li>An abstract getter annotated with {@link InputArtifact} will receive the <em>input artifact</em> location, which is the file or directory that the transform should be applied to.</li>
 *      <li>An abstract getter with {@link InputArtifactDependencies} will receive the <em>dependencies</em> of its input artifact.</li>
 *  </ul>
 *
 * @param <T> Parameter type for the transform action. Should be {@link TransformParameters.None} if the action does not have parameters.
 * @since 5.3
 */
public interface TransformAction<T extends TransformParameters> {

    /**
     * The object provided by {@link TransformSpec#getParameters()} when registering the artifact transform.
     *
     * <p>
     *     Do not implement this method in your subclass.
     *     Gradle provides the implementation when registering the transform action via {@link org.gradle.api.artifacts.dsl.DependencyHandler#registerTransform(Class, Action)}.
     * </p>
     */
    @Inject
    T getParameters();

    /**
     * Executes the transform.
     *
     * <p>This method must be implemented in the subclass.</p>
     *
     * @param outputs Receives the outputs of the transform.
     */
    void transform(TransformOutputs outputs);
}
