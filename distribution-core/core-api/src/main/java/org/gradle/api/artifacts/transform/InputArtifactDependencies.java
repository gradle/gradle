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

import org.gradle.api.file.FileCollection;
import org.gradle.api.reflect.InjectionPointQualifier;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Attach this annotation to an abstract getter that should receive the <em>artifact dependencies</em> of the {@link InputArtifact} of an artifact transform.
 *
 * <p>
 *     For example, when a project depends on {@code spring-web}, when the project is transformed (i.e. the project is the input artifact),
 *     the input artifact dependencies are the file collection containing the {@code spring-web} JAR and all its dependencies like e.g. the {@code spring-core} JAR.
 *
 *     The abstract getter must be declared as type {@link org.gradle.api.file.FileCollection}.
 *     The order of the files matches that of the dependencies declared for the input artifact.
 * </p>
 *
 * <p>Example usage:</p>
 * <pre class='autoTested'>
 * import org.gradle.api.artifacts.transform.TransformParameters;
 *
 * public abstract class MyTransform implements TransformAction&lt;TransformParameters.None&gt; {
 *
 *     {@literal @}InputArtifact
 *     public abstract Provider&lt;FileSystemLocation&gt; getInputArtifact();
 *
 *     {@literal @}InputArtifactDependencies
 *     public abstract FileCollection getDependencies();
 *
 *     {@literal @}Override
 *     public void transform(TransformOutputs outputs) {
 *         FileCollection dependencies = getDependencies();
 *         // Do something with the dependencies
 *     }
 * }
 * </pre>
 *
 * @since 5.3
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
@Documented
@InjectionPointQualifier(supportedTypes = FileCollection.class)
public @interface InputArtifactDependencies {
}
