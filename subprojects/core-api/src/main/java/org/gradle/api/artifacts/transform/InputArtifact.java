/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.provider.Provider;
import org.gradle.api.reflect.InjectionPointQualifier;

import java.io.File;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Attach this annotation to an abstract getter that should receive the <em>input artifact</em> for an artifact transform.
 * This is the artifact that the transform is applied to.
 *
 * <p>The abstract getter must be declared as type {@link Provider}&lt;{@link org.gradle.api.file.FileSystemLocation}&gt;.</p>
 *
 * <p>Example usage:</p>
 *
 * <pre class='autoTested'>
 * import org.gradle.api.artifacts.transform.TransformParameters;
 *
 * public abstract class MyTransform implements TransformAction&lt;TransformParameters.None&gt; {
 *
 *     {@literal @}InputArtifact
 *     public abstract Provider&lt;FileSystemLocation&gt; getInputArtifact();
 *     {@literal @}Override
 *     public void transform(TransformOutputs outputs) {
 *         File input = getInputArtifact().get().getAsFile();
 *         // Do something with the input
 *     }
 * }
 * </pre>
 *
 * @since 5.3
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
@Documented
@InjectionPointQualifier(supportedTypes = { File.class }, supportedProviderTypes = { FileSystemLocation.class })
public @interface InputArtifact {
}
