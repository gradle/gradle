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

import com.google.common.io.Files
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Specification

@Requires(TestPrecondition.NOT_WINDOWS)
class NativeFilePermissionHandlerTest extends Specification {

    @Requires(TestPrecondition.FILE_PERMISSIONS)
    def "changing files permissions"(){
        setup:
        def File dir = Files.createTempDir();
        def File file = new File(dir, "f")
        Files.touch(file)

        NativeFilePermissionHandler handler = new NativeFilePermissionHandler(null)
        when:
        handler.chmod(file, mode);
        then:
        mode == (PosixUtil.current().stat(file.getAbsolutePath()).mode() & 0777);
        where:
        mode << [0722, 0644, 0744, 0755]
    }

    @Requires(TestPrecondition.UNKNOWN_OS)
    def "changing files permissions not supported for unknown os"(){
        setup:
        def File dir = Files.createTempDir();
        def File file = new File(dir, "f")
        Files.touch(file)
        NativeFilePermissionHandler handler = new NativeFilePermissionHandler(null)
        def originMode = (PosixUtil.current().stat(file.getAbsolutePath()).mode() & 0777);

        when:
        handler.chmod(file, mode);
        then:
        originMode == (PosixUtil.current().stat(file.getAbsolutePath()).mode() & 0777);
        where:
        mode << [0722, 0644, 0744, 0755]
    }
}
