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

class SwiftAlternateLib extends SourceElement implements GreeterElement, SumElement, MultiplyElement {
    def alternateGreeter = new SwiftAlternateGreeter()
    def sum = new SwiftSum()
    def multiply = new SwiftMultiply()

    @Override
    List<SourceFile> getFiles() {
        return [alternateGreeter.sourceFile, sum.sourceFile, multiply.sourceFile]
    }

    @Override
    String getExpectedOutput() {
        return alternateGreeter.expectedOutput
    }

    @Override
    int sum(int a, int b) {
        return sum.sum(a, b)
    }

    @Override
    int multiply(int a, int b) {
        return multiply.multiply(a, b)
    }
}
