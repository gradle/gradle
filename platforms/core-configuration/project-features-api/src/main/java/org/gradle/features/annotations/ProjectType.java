/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.features.annotations;

import org.gradle.api.Incubating;
import org.gradle.features.binding.SchemaProjectTypeApplyAction;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation placed on a {@link SchemaProjectTypeApplyAction} implementation to register it as a
 * project type. When the plugin id whose descriptor points at the annotated apply action is applied
 * in a settings {@code plugins { }} block, the project type is registered directly under the given
 * {@link #name()} — no project plugin shell or binding class is required.
 *
 * <p>The project type's definition type is inferred from the apply action's generic type parameter.
 *
 * @since 9.7.0
 */
@Incubating
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ProjectType {
    /**
     * The name of the project type. This is how it will be referenced in the DSL.
     *
     * @since 9.7.0
     */
    String name();
}
