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

import org.gradle.integtests.fixtures.SourceFile

/**
 * A single module Swift app, with several source files.
 */
class SwiftApp extends SourceElement implements AppElement {
    final greeter = new SwiftGreeter()
    final sum = new SwiftSum()
    final main = new SwiftMain(greeter, sum)
    final List<SourceFile> files = [main.sourceFile, greeter.sourceFile, sum.sourceFile]

    @Override
    String getExpectedOutput() {
        return main.expectedOutput
    }
}
