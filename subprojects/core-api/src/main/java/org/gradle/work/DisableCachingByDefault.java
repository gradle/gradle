/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.work;

import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskOutputs;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Attached to a task or artifact transform type to indicate that task output caching should be disabled by default for work of this type.
 *
 * Not all work benefits from caching: for example tasks that only copy content around on disk rarely do.
 * This annotation allows clearly stating that work does not benefit from caching.
 * It also allows attaching an explanation about why a certain work unit is not made cacheable.
 *
 * <p>Caching for individual task instances can be enabled and disabled via {@link TaskOutputs#cacheIf(String, Spec)} or disabled via {@link TaskOutputs#doNotCacheIf(String, Spec)}.
 * Using these APIs takes precedence over the presence (or absence) of {@code @DisableCachingByDefault}.</p>
 *
 * @see org.gradle.api.tasks.CacheableTask
 * @see org.gradle.api.artifacts.transform.CacheableTransform
 *
 * @since 7.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface DisableCachingByDefault {
    String because() default "";
}
