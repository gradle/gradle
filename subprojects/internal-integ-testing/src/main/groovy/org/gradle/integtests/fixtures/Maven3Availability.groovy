/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.integtests.fixtures

import org.gradle.process.internal.ExecHandleBuilder

/**
 * by Szczepan Faber, created at: 9/4/12
 */
class Maven3Availability {

    public final static boolean AVAILABLE = findOut();

    private static boolean findOut() {
        def out = new ByteArrayOutputStream()
        def exec = new ExecHandleBuilder()
                //this only works on linux but that's enough for me at this time.
                //this class goes when we can use maven3 classes for building pom models.
                .commandLine("mvn", "-v")
                .workingDir(new File(".").absoluteFile)
                .setStandardOutput(out)
                .redirectErrorStream()
                .build()

        try {
            exec.start()
        } catch (Exception e) { //not available
            return false
        }
        def result = exec.waitForFinish()
        return result.exitValue == 0 && out.toString().startsWith("Apache Maven 3")
    }
}
