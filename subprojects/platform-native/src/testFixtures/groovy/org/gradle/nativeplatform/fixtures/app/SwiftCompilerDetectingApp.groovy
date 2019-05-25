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

import org.gradle.integtests.fixtures.SourceFile

class SwiftCompilerDetectingApp extends SourceFileElement implements AppElement {
    def swiftVersion

    SwiftCompilerDetectingApp(swiftVersion) {
        this.swiftVersion = swiftVersion
    }

    @Override
    SourceFile getSourceFile() {
        return sourceFile('swift', 'main.swift', """
            #if swift(>=5.0)
                print("Compiled using Swift 5.x compiler")
            #elseif swift(>=4.0)
                print("Compiled using Swift 4.x compiler")
            #elseif swift(>=3.0)
                print("Compiled using Swift 3.x compiler")
            #else
                print("Compiled using unknown compiler")
            #endif
        """)
    }

    @Override
    String getExpectedOutput() {
        return "Compiled using Swift ${swiftVersion}.x compiler\n"
    }
}
