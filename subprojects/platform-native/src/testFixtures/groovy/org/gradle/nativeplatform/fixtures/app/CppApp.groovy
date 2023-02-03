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
 * A single project C++ app, with several source files.
 */
class CppApp extends CppSourceElement implements AppElement {
    final greeter = new CppGreeter()
    final sum = new CppSum()
    final main = new CppMain(greeter, sum)

    @Override
    SourceElement getSources() {
        return ofElements(main, greeter.sources, sum.sources)
    }

    @Override
    SourceElement getHeaders() {
        return ofElements(greeter.headers, sum.headers)
    }

    @Override
    String getExpectedOutput() {
        return main.expectedOutput
    }
}
