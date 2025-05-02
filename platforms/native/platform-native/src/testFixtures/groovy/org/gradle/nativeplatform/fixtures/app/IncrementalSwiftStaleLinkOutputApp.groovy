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
 * A Swift app that remove all sources.
 */
class IncrementalSwiftStaleLinkOutputApp extends IncrementalSwiftElement implements AppElement {
    private final greeter = new SwiftGreeter()
    private final sum = new SwiftSum()
    private final main = new SwiftMain(greeter, sum)

    final List<IncrementalElement.Transform> incrementalChanges = [
        delete(greeter), delete(sum), delete(main)]
    final String expectedOutput = main.expectedOutput
    final String moduleName = "App"
}
