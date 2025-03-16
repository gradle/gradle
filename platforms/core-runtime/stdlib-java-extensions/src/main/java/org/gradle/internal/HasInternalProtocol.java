/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.internal;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that there is an internal complementary protocol to the public type that is annotated with this.
 *
 * This should only be used on a type that is always assumed to also implement the internal protocol by Gradle internals.
 *
 * This exists to help anyone reading the source code realise that there is an internal component to the type.
 */
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE})
public @interface HasInternalProtocol {

}
