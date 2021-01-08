/*
 * Copyright 2018 the original author or authors.
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

import com.google.common.collect.Iterables
import com.google.common.collect.Lists
import org.gradle.integtests.fixtures.SourceFile

class SwiftAppWithLibrariesAndXCTest extends MainWithXCTestSourceElement implements AppElement {
    final SwiftAppWithLibraries app = new SwiftAppWithLibraries()
    final SwiftSourceElement main = app.application
    final SwiftSum sum = new SwiftSum()
    final SwiftMultiply multiply = new SwiftMultiply()
    final XCTestSourceElement test = new SwiftAppTest(main, app.greeter, sum, multiply)

    String expectedOutput = main.expectedOutput

    SwiftAppWithLibrariesAndXCTest() {
        super('app')
    }

    SourceElement getGreeter() {
        return app.greeter
    }

    SourceElement getLogger() {
        return app.logger
    }

    @Override
    List<SourceFile> getFiles() {
        return Lists.newArrayList(Iterables.concat(super.getFiles(), app.greeter.files, app.logger.files, sum.files, multiply.files))
    }
}
