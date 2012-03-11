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

package org.gradle.internal.nativeplatform.filesystem.jdk7

import spock.lang.Specification
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import com.google.common.io.Files
import org.gradle.internal.nativeplatform.filesystem.FilePermissionHandlerFactory


class PosixJdk7FilePermissionHandlerTest extends Specification {

    @Requires(TestPrecondition.NOT_WINDOWS)
    def "test chmod on non windows platforms with JDK7"() {
        setup:
        def File dir = Files.createTempDir();
        def File file = new File(dir, "f")
        Files.touch(file)
        def handler = FilePermissionHandlerFactory.createDefaultFilePermissionHandler()
        when:
        handler.chmod(file, mode);
        then:
        mode == handler.getUnixMode(file);
        where:
        mode << [0722, 0644, 0744, 0755]
    }

}
