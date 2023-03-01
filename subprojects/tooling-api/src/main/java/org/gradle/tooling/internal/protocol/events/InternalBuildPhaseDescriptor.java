/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.tooling.internal.protocol.events;

/**
 * DO NOT CHANGE THIS INTERFACE. It is part of the cross-version protocol.
 *
 * Returns build phase details.
 *
 * @since 7.6
 */
public interface InternalBuildPhaseDescriptor extends InternalOperationDescriptor {

    /**
     * Returns the build phase name.
     *
     * Can be one of: CONFIGURE_ROOT_BUILD, CONFIGURE_BUILD, RUN_MAIN_TASKS, RUN_WORK.
     */
    String getBuildPhase();

    /**
     * Returns number of build items this phase will execute.
     */
    int getBuildItemsCount();
}
