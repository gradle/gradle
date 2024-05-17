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

package org.gradle.debug

import org.gradle.launcher.Main

/**
 * Use this class to run/debug any gradle build from the IDE. Especially useful for learning & debugging Gradle internals.
 */
class GradleBuildRunner {
    public static void main(String[] args) {
        //simplest example:
        Main.main("-v")

        //To run/debug any build from the IDE you can tweak the parameters:
        //org.gradle.launcher.Main.main("-p", "/path/to/project/you/want/to/build", "someTask", "someOtherTask");
    }
}
