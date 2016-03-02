/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.internal.os.OperatingSystem
import org.gradle.process.internal.BadExitCodeException

class MavenInstallation {

    private static final boolean IS_WINDOWS = OperatingSystem.current().isWindows()

    final String version
    final File home
    final File mvn

    public MavenInstallation(String version, File home) {
        this.version = version
        this.home = home
        this.mvn = findMvnExecutable(home)
    }

    static File findMvnExecutable(File home) {
        def bin = new File(home, "bin")
        if (IS_WINDOWS) {
            // Maven moved from .bat to .cmd starting with 3.3.1 (3.3.0 was canceled)
            def bat = new File(bin, "mvn.bat")
            return bat.isFile() ? bat : new File(bin, "mvn.cmd")
        }
        return new File(bin, "mvn")
    }

    static boolean valid(File home) {
        def mvn = findMvnExecutable(home)
        home.isDirectory() && mvn.isFile()
    }

    static String probeVersion(File home) {
        def mvn = findMvnExecutable(home)
        def env = System.getenv().findAll { it.key != "M2" && it.key != "M2_HOME" }.collect { "${it.key}=${it.value}" }
        def process = [mvn.absolutePath, "--version"].execute(env, home)
        def exitValue = process.waitFor()
        if (exitValue != 0) {
            throw new BadExitCodeException("Unable to probe Maven version from ${mvn.absolutePath}, returned ${exitValue}.\n${process.err.text}")
        }
        (process.text.readLines().get(0) =~ /Apache Maven ([^\s]+) \(/)[0][1]
    }
}
