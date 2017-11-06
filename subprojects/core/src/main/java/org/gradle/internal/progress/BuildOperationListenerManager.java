/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.progress;

/**
 * Manages listeners of build operations.
 *
 * Be aware that there are two instances of this within the services hierarchy.
 * One is global scoped (used by TAPI infrastructure).
 * The other is build session scoped (used by build operation notifications).
 *
 * @since 3.5
 */
public interface BuildOperationListenerManager {

    void addListener(BuildOperationListener listener);

    void removeListener(BuildOperationListener listener);

}
