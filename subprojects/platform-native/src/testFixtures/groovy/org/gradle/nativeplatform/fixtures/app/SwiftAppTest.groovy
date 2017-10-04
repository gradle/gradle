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

class SwiftAppTest extends XCTestSourceElement {
    final greeterTest
    final sumTest
    final multiplyTest

    SwiftAppTest(GreeterElement greeter, SumElement sum, MultiplyElement multiply) {
        greeterTest = new SwiftGreeterTest(greeter).withImport("App")
        sumTest = new SwiftSumTest(sum).withImport("App")
        multiplyTest = new SwiftMultiplyTest(multiply).withTestableImport("App")
    }

    final List<XCTestSourceFileElement> getTestSuites() {
        [greeterTest, sumTest, multiplyTest]
    }

    @Override
    List<SourceFile> getFiles() {
        return super.files
    }
}
