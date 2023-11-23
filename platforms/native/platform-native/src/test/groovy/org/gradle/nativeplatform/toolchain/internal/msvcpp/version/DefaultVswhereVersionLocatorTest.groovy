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

package org.gradle.nativeplatform.toolchain.internal.msvcpp.version

class DefaultVswhereVersionLocatorTest extends VswhereSpec {
    def locator = new DefaultVswhereVersionLocator(windowsRegistry, os)

    def "finds vswhere executable in Program Files"() {
        given:
        vswhereInProgramFiles()

        expect:
        locator.getVswhereInstall().absolutePath == vswhere.absolutePath
    }

    def "finds vswhere executable in Program Files (X86)"() {
        given:
        vswhereInProgramFilesX86()

        expect:
        locator.getVswhereInstall().absolutePath == vswhere.absolutePath
    }

    def "finds vswhere executable in system path"() {
        given:
        vswhereInPath()

        expect:
        locator.getVswhereInstall().absolutePath == vswhere.absolutePath
    }

    def "finds vswhere executable in system path if vswhere executable in Program Files is not a file"() {
        def programFilesVswhere = programFiles.createDir("Microsoft Visual Studio/Installer").createDir("vswhere.exe")

        given:
        vswhereInPath()
        assert vswhere.absolutePath != programFilesVswhere.absolutePath

        expect:
        locator.getVswhereInstall().absolutePath == vswhere.absolutePath
    }

    def "prefers vswhere executable from Program Files if also available in system path"() {
        def pathVswhere = localRoot.createFile("vswhere.exe")

        given:
        vswhereInProgramFiles()
        _ * os.findInPath("vswhere.exe") >> pathVswhere
        assert pathVswhere.absolutePath != vswhere.absolutePath

        expect:
        locator.getVswhereInstall().absolutePath == vswhere.absolutePath
    }

    def "prefers vswhere executable from Program Files (X86) if also available in system path"() {
        def pathVswhere = localRoot.createFile("vswhere.exe")

        given:
        vswhereInProgramFilesX86()
        _ * os.findInPath("vswhere.exe") >> pathVswhere
        assert pathVswhere.absolutePath != vswhere.absolutePath

        expect:
        locator.getVswhereInstall().absolutePath == vswhere.absolutePath
    }

    @Override
    void vswhereInPath() {
        super.vswhereInPath()
        1 * os.findInPath(_) >> vswhere
    }

    @Override
    void vswhereNotFound() {
        super.vswhereNotFound()
        1 * os.findInPath(_) >> null
    }

    @Override
    void vswhereNotFoundX86Registry() {
        super.vswhereNotFoundX86Registry()
        1 * os.findInPath(_) >> null
    }
}
