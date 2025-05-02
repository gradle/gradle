/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.tooling.events.test;

import org.gradle.tooling.events.ProgressEvent;

/**
 * An event that informs about a test printing text to the standard output or to the standard error.
 * <p>
 * A new test output event instance is created for each line of text printed by the test.
 *
 * @since 6.0
 */
public interface TestOutputEvent extends ProgressEvent {

    @Override
    TestOutputDescriptor getDescriptor();
}
