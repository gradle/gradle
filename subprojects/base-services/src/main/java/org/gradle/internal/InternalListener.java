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

package org.gradle.internal;

/**
 * Indicator that the object should not be considered user code.
 * <p>
 * Execution of user code listeners are observable as build operations, with provenance information.
 * Internal listeners are not.
 * This is implemented by decorating user code listeners at the registration site.
 * This interface is used to suppress the decoration.
 * <p>
 * User can generally do very little about internal listeners (i.e. they are a fixed cost),
 * while they do have control of user code listeners.
 * <p>
 * There are some reusable implementations of this, such as {@link org.gradle.api.internal.InternalAction}.
 */
public interface InternalListener {
}
