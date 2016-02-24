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
        def mvnName = IS_WINDOWS ? "mvn.bat" : "mvn"
        new File(home, "bin${File.separator}$mvnName")
    }

    static boolean valid(File home) {
        def mvn = findMvnExecutable(home)
        home.isDirectory() && home.canExecute() && mvn.isFile() && mvn.canExecute()
    }

    static String probeVersion(File home) {
        def mvn = findMvnExecutable(home)
        def banner = [mvn.absolutePath, "--version"].execute().text
        (banner.readLines().get(0) =~ /Apache Maven ([^\s]+) \(/)[0][1]
    }
}
