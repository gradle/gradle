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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Attaching this annotation to a {@link TransformAction} type it indicates that the build cache should be used for artifact transforms of this type.
 *
 * <p>Only an artifact transform that produces reproducible and relocatable outputs should be marked with {@code CacheableTransform}.</p>
 *
 * <p>
 *     Normalization must be specified for each file parameter of a cacheable transform.
 *     For example:
 * </p>
 * <pre class='autoTested'>
 * import org.gradle.api.artifacts.transform.TransformParameters;
 *
 * {@literal @}CacheableTransform
 * public abstract class MyTransform implements TransformAction&lt;TransformParameters.None&gt; {
 *     {@literal @}PathSensitive(PathSensitivity.NAME_ONLY)
 *     {@literal @}InputArtifact
 *     public abstract Provider&lt;FileSystemLocation&gt; getInputArtifact();
 *
 *     {@literal @}Classpath
 *     {@literal @}InputArtifactDependencies
 *     public abstract FileCollection getDependencies();
 *
 *     {@literal @}Override
 *     public void transform(TransformOutputs outputs) {
 *         // ...
 *     }
 * }
 * </pre>
 *
 * @since 5.3
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface CacheableTransform {
}
