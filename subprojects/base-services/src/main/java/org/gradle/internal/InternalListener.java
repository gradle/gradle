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
 * Interface to be implemented when registering internal actions or listeners that should not emit build
 * operations.
 *
 * <p>
 *     When registering listeners on Gradle, Project and TaskExecutionGraph instances, user code is
 *     decorated to emit build operations around the execution of that code. This is to support
 *     attribution of the time taken etc by those listeners to the script or plugin that registered the
 *     listener. For gradle internals, we don't want to do that, so we mark internal cases using this
 *     interface. See also the convenience class/interfaces InternalAction and InternalBuildAdapter.
 * </p>
 */
public interface InternalListener {
}
