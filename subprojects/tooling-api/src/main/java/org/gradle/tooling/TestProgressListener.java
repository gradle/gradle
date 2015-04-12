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
package org.gradle.tooling;

import org.gradle.api.Incubating;

/**
 * A listener which is notified when the tests that are executed as part of running a build make progress.
 *
 * @see BuildLauncher#addTestProgressListener(TestProgressListener)
 * @since 2.4
 */
@Incubating
public interface TestProgressListener {

    /**
     * Called when the test execution progresses.
     *
     * The following events are currently issued:
     * <ul>
     *     <li>TestStartedEvent</li>
     *     <li>TestSkippedEvent</li>
     *     <li>TestSucceededEvent</li>
     *     <li>TestFailedEvent</li>
     *     <li>TestSuiteStartedEvent</li>
     *     <li>TestSuiteSkippedEvent</li>
     *     <li>TestSuiteSucceededEvent</li>
     *     <li>TestSuiteFailedEvent</li>
     * </ul>
     *
     * @param event An event describing the test status change.
     */
    void statusChanged(TestProgressEvent event);

}
