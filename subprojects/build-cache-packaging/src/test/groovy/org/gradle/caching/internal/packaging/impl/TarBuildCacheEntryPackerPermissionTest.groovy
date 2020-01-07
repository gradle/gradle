/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.caching.internal.packaging.impl

import org.gradle.internal.file.Deleter
import spock.lang.Unroll

import static org.gradle.internal.file.TreeType.FILE

class TarBuildCacheEntryPackerPermissionTest extends AbstractTarBuildCacheEntryPackerSpec {

    @Override
    protected Deleter createDeleter() {
        Mock(Deleter)
    }

    @Unroll
    def "can pack single file with file mode #mode"() {
        given:
        def sourceOutputFile = temporaryFolder.file("source.txt") << "output"
        def unixMode = Integer.parseInt(mode, 8)
        PermissionUtils.setPermissions(sourceOutputFile, unixMode)

        and:
        def targetOutputFile = temporaryFolder.file("target.txt")
        def output = new ByteArrayOutputStream()

        when:
        pack output, prop(FILE, sourceOutputFile)

        and:
        def input = new ByteArrayInputStream(output.toByteArray())
        unpack input, prop(FILE, targetOutputFile)

        then:
        targetOutputFile.text == "output"
        PermissionUtils.permissions(targetOutputFile) == unixMode

        where:
        mode << [ "0644", "0755" ]
    }
}
