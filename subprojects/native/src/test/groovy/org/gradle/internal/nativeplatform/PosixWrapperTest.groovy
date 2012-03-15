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

package org.gradle.internal.nativeplatform

import org.gradle.internal.nativeplatform.filesystem.FileSystem
import spock.lang.Specification
import org.jruby.ext.posix.*

class PosixWrapperTest extends Specification {

    def void "wraps JavaPOSIX to ChmodDisabledFallbackPOSIX"() {
        JavaPOSIX posix = Mock()
        when:
        def wrappedPosix = PosixWrapper.wrap(posix)
        then:
        wrappedPosix instanceof PosixWrapper.ChmodDisabledFallbackPOSIX
    }

    def void "wraps WindowsPOSIX to FilePermissionFallbackPOSIX"() {
        WindowsPOSIX posix = Mock()
        when:
        def wrappedPosix = PosixWrapper.wrap(posix)
        then:
        wrappedPosix instanceof PosixWrapper.FilePermissionFallbackPOSIX
    }

    def void "returns input object for other than JavaPOSIX implementations"() {
        when:
        def originPosix = Mock(SolarisPOSIX)
        def posix = PosixWrapper.wrap(originPosix)
        then:
        originPosix == posix

        when:
        originPosix = Mock(BaseNativePOSIX)
        posix = PosixWrapper.wrap(originPosix)
        then:
        originPosix == posix
    }

    def "FilePermissionFallbackPOSIX does not delegate chmod calls"() {
        setup:
        POSIX delegatePosix = Mock()
        def posix = new PosixWrapper.ChmodDisabledFallbackPOSIX(delegatePosix)
        when:
        posix.chmod("filepath", 755)
        then:
        0 * delegatePosix.chmod(_, _)
    }

    def "ChmodDisabledFallbackPOSIX wraps filestat chmod calls"() {
        setup:
        POSIX delegatePosix = Mock()
        def posix = new PosixWrapper.ChmodDisabledFallbackPOSIX(delegatePosix)

        when:
        def fileStat = posix.stat("filepath")

        then:
        fileStat instanceof PosixWrapper.FallbackFileStat

    }

    def "FallbackFileStat mode() returns 755 for directories"() {
        setup:
        FileStat delegate = Mock()
        PosixWrapper.FallbackFileStat filestat = new PosixWrapper.FallbackFileStat(delegate);
        delegate.isDirectory() >> true

        when:
        def mode = filestat.mode();

        then:
        mode == FileSystem.DEFAULT_DIR_MODE
    }

    def "FallbackFileStat mode() returns 644 for non directories"() {
        setup:
        FileStat delegate = Mock()
        PosixWrapper.FallbackFileStat filestat = new PosixWrapper.FallbackFileStat(delegate);
        delegate.isDirectory() >> false

        when:
        def mode = filestat.mode();

        then:
        mode == FileSystem.DEFAULT_FILE_MODE
    }
}
