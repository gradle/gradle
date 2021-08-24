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

package org.gradle.internal.execution.steps;

import org.gradle.caching.internal.origin.OriginMetadata;

public interface CurrentSnapshotResult extends SnapshotResult {
    /**
     * Returns the origin metadata of the finished work's output.
     * The metadata could refer to the current execution if the work had to be executed,
     * or to another build if the work was reused.
     */
    OriginMetadata getOriginMetadata();

    /**
     * Did we reuse the output from some previous execution?
     */
    boolean isReused();
}
