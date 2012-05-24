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

package org.gradle.internal.nativeplatform.filesystem

import org.junit.Rule
import spock.lang.Specification
import org.gradle.util.TemporaryFolder
import org.jruby.ext.posix.POSIX

class FallbackPOSIXTest extends Specification {

    FallbackPOSIX posix = new FallbackPOSIX()

    @Rule TemporaryFolder tempFolder;

    def "returns 0 on chmod calls"() {
        setup:
        def testFile = tempFolder.createFile("testFile");
        expect:
        0 == posix.chmod(testFile.absolutePath, mode)
        where:
        mode << [644, 755, 777];
    }

    def "stat() returns instance of FallbackStat"() {
        setup:
        def testFile = tempFolder.createDir();
        when:
        def stat = posix.stat(testFile.absolutePath)
        then:
        stat instanceof FallbackFileStat
    }

    def "returns errno code 1 (ENOTSUP) for symlink calls"() {
        expect:
        1 == posix.symlink("/old/path", "new/path")
    }
}
