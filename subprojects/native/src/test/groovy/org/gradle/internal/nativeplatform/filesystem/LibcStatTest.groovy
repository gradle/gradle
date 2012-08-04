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

package org.gradle.internal.nativeplatform.filesystem;

import spock.lang.Specification
import org.gradle.internal.os.OperatingSystem
import org.gradle.internal.nativeplatform.jna.LibC
import org.jruby.ext.posix.BaseNativePOSIX
import spock.lang.Unroll
import org.jruby.ext.posix.FileStat;

class LibcStatTest extends Specification {

    OperatingSystem os = Mock();
    LibC libc = Mock()
    BaseNativePOSIX posix = Mock()
    FilePathEncoder encoder = Mock()

    @Unroll
    def "LibCStat on #osname maps to libc #libcMethodName"(){
        given:
        1 * os.isMacOsX() >> (osname == "macosx");
        1 * posix.allocateStat() >> Mock(FileStat)
        File testFile = new File("a/file/path")
        LibCStat libCStat = new LibCStat(libc, os, posix, encoder);
        
        when:
        libCStat.getUnixMode(testFile)

        then:
        1 * libc."${libcMethodName}"(*_);
        where:
        osname   | libcMethodName
        "macosx" | "stat"
        "linux"  | "__xstat64"
    }
}
