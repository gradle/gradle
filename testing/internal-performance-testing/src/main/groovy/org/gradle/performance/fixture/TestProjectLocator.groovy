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

import groovy.transform.CompileStatic
import org.gradle.test.fixtures.dsl.GradleDsl

@CompileStatic
class TestProjectLocator {

    static File findProjectDir(String name) {
        def dir = new File("build/$name").absoluteFile
        if (!dir.directory) {
            throw new IllegalArgumentException("Did not find test project at: '$dir.absolutePath'. Please run 'gradlew $name' to generate the test project.")
        }

        if (!hasSettingsScript(dir)) {
            throw new IllegalArgumentException("Test project at '$dir.absolutePath' does not have a settings script. Please add one.")
        }
        dir
    }

    private static boolean hasSettingsScript(File dir) {
        for (GradleDsl dsl : GradleDsl.values()) {
            if (new File(dir, dsl.fileNameFor("settings")).file) {
                return true;
            }
        }
        return false;
    }

}
