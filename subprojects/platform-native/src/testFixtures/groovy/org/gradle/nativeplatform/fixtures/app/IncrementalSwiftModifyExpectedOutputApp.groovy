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

package org.gradle.nativeplatform.fixtures.app

/**
 * A Swift app with a changed source file
 */
class IncrementalSwiftModifyExpectedOutputApp extends IncrementalSwiftElement {
    private final greeter = new SwiftGreeter()
    private final sum = new SwiftSum()
    private final main = new SwiftMain(greeter, sum)
    private final alternateMain = new SwiftAlternateMain(greeter)

    IncrementalSwiftModifyExpectedOutputApp() {
        // Verify some assumptions that the tests make
        assert original.files.size() > 1
        assert alternate.files.size() == original.files.size()
    }

    final List<IncrementalElement.Transform> transforms = [
        identity(greeter), identity(sum), identity(main), modify(main, alternateMain)]
    final String expectedOutput = main.expectedOutput
    final String expectedAlternateOutput = alternateMain.expectedOutput
    final String moduleName = "App"
}
