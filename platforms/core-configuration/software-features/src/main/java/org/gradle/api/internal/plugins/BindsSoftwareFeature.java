/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.plugins;

import org.gradle.api.Incubating;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation that can be applied to implementations of {@link SoftwareFeatureBindingRegistration}
 * to indicate that the registration should be automatically discovered and applied by Gradle.
 *
 * This annotation is used by project plugins that want to contribute software feature bindings.
 */
@Incubating
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface BindsSoftwareFeature {
    Class<? extends SoftwareFeatureBindingRegistration> value();
}
