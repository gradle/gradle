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

class SwiftAppWithXCTest extends MainWithXCTestSourceElement implements AppElement {
    private final app = new SwiftApp()
    final SwiftSourceElement main = app.asModule("App")
    final XCTestSourceElement test = new SwiftAppTest(app.greeter, app.sum, app.multiply).asModule("AppTest").withInfoPlist()

    String expectedOutput = app.expectedOutput
}
