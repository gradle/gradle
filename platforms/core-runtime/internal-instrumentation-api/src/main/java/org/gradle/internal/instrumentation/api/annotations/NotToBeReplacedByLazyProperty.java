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

package org.gradle.internal.instrumentation.api.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks that a property will be kept as eager property also after migration.
 * This is a complementary annotation to {@link ToBeReplacedByLazyProperty}.
 *
 * This annotation is internal even though it's attached to a public API, so it should not be referenced in third-party code.
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD})
public @interface NotToBeReplacedByLazyProperty {

    /**
     * Reason why this property will be kept eager.
     */
    String because();

    /**
     * Marks that a property will get deprecated with Provider API migration.
     */
    boolean willBeDeprecated() default false;
}
