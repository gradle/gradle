/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.performance.fixture

class TestProjectLocator {

    File findProjectDir(String name) {
        def base = "subprojects/performance/build"
        def locations = ["$base/$name", "../../$base/$name"]
        def dirs = locations.collect { new File(it).absoluteFile }
        for (File dir: dirs) {
            if (dir.isDirectory()) {
                return dir.canonicalFile
            }
        }
        def message = "Looks like the test project '$name' was not generated.\nI've tried to find it at:\n"
        dirs.each { message += "  $it\n" }
        message +="Please run 'gradlew performance:$name' to generate the test project."
        throw new IllegalArgumentException(message)
    }
}
