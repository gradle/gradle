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

package org.gradle.tooling.internal.protocol.events;

/**
 * DO NOT CHANGE THIS INTERFACE. It is part of the cross-version protocol.
 *
 * @since 6.0
 */
public interface InternalTestSuccessResult2 extends InternalTestSuccessResult {

    // TODO (donat) Find a better naming scheme for all test result interfaces/classes. Something like TestSuccessResultWithOutput.
    // TODO (donat) Providing the test output potentially add big memory pressure. We should control the output collection.
    //              Maybe add a new TestLauncher.filterTestOutput(TestOutputFilter) method have the following properties:
    //               INCLUDE_ALL
    //               FILTER_ALL
    //               TRIM (default; include test output only if it doesn't reach, say 50Mb altogether)
    // TODO (donat) Add sufficient test coverage for all changed classes

    String getOutput();
    String getError();
}
