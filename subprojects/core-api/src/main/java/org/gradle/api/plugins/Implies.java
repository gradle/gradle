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

package org.gradle.api.plugins;

import org.gradle.api.Incubating;
import org.gradle.api.Plugin;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * When applied to a Gradle {@link org.gradle.api.Plugin}, the annotation tells Gradle
 * that whenever the annotated plugin is applied, the implied plugin must also be applied to
 * all applicable entities from the same build.
 *
 * The current target of the annotated plugin is always applicable, in other words,
 * a {@link org.gradle.api.invocation.Gradle} plugin can imply another {@code Gradle} plugin, which would be applied against the
 * same target, <b>after</b> the implying plugin has been successfully applied, the same is true for {@link org.gradle.api.initialization.Settings}
 * and {@link org.gradle.api.Project} plugins.
 *
 * In addition, depending on the annotated plugin target, the set of applicable entities can be larger:
 * <ul>
 *     <li>in the case of a {@code Gradle} plugin, an implied {@code Settings} plugin would be applied to the
 *     {@code Settings} object while an implied {@code Project} plugin would be applied to every {@code Project} instance</li>
 *     <li>in the case of a {@code Settings} plugin, an implied {@code Project} plugin would be applied to every {@code Project} instance</li>
 * </ul>
 *
 * @since 8.7
 */
@Target({ElementType.TYPE})
@Incubating
public @interface Implies {

    /**
     * The implied plugin implementation.
     *
     * @since 8.7
     */
    Class<? extends Plugin<?>> value();
}
