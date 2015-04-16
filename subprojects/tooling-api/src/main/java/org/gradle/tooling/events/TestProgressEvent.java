/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.tooling.events;

import org.gradle.api.Incubating;
import org.gradle.tooling.TestDescriptor;

/**
 * Some information about the progress of executing the test as part of running a build.
 *
 * Note: sub-types of this interface provide more specific information.
 *
 * @since 2.4
 */
@Incubating
public interface TestProgressEvent extends Event {

    /**
     * Returns a short description of the event.
     *
     * @return The short description of the event
     */
    String getDescription();

    /**
     * The description of the test for which progress is reported.
     *
     * @return The test description
     */
    TestDescriptor getTestDescriptor();

    /**
     * The type of the test (suite or atomic for example).
     *
     * @return the test kind
     */
    TestKind getTestKind();

}
