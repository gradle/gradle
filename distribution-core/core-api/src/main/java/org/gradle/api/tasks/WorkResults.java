/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.tasks;

/**
 * Helps access trivial {@link WorkResult} objects.
 *
 * @since 4.2
 */
public class WorkResults {
    private static final WorkResult DID_WORK = () -> true;
    private static final WorkResult DID_NO_WORK = () -> false;

    private WorkResults() {}

    /**
     * Returns a {@link WorkResult} object representing work done according to the given parameter.
     */
    public static WorkResult didWork(boolean didWork) {
        return didWork ? DID_WORK : DID_NO_WORK;
    }
}
