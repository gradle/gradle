/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.os

import spock.lang.IgnoreIf
import spock.lang.Specification

@IgnoreIf({ !OperatingSystem.current().windows })
class MyWindowsFileSystemTest extends Specification {
    def fs = MyFileSystem.current()

    def "is case insensitive"() {
        expect:
        !fs.caseSensitive
    }

    def "is not symlink aware"() {
        expect:
        !fs.symlinkAware
    }

    def "does not implicitly lock file on open"() {
        expect:
        !fs.implicitlyLocksFileOnOpen
    }
}