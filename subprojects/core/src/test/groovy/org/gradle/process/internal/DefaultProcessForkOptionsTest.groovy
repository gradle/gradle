/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.process.internal

import org.gradle.api.internal.file.FileResolver
import org.gradle.util.TestUtil
import spock.lang.Specification

class DefaultProcessForkOptionsTest extends Specification {
    def baseDir = new File("base-dir")
    def resolver = Mock(FileResolver.class) {
        resolve(".") >> baseDir
    }
    def options = TestUtil.newInstance(DefaultProcessForkOptions, TestUtil.objectFactory(), resolver)

    def defaultValues() {
        expect:
        options.executable.getOrNull() == null
        !options.environment.get().empty
    }

    def convertsEnvironmentToString() {
        when:
        options.environment = [key1: 12, key2: "${1+2}", key3: "null"]

        then:
        options.actualEnvironment == [key1: '12', key2: '3', key3: 'null']
    }

    def canAddEnvironmentVariables() {
        when:
        options.environment = [:]

        then:
        options.environment.get() == [:]

        when:
        options.environment('key', 12)

        then:
        options.environment.get() == [key: 12]
        options.actualEnvironment == [key: '12']

        when:
        options.environment(key2: "value")

        then:
        options.environment.get() == [key: 12, key2: "value"]
    }

    def canCopyToTargetOptions() {
        given:
        def target = TestUtil.newInstance(DefaultProcessForkOptions, TestUtil.objectFactory(), resolver)

        when:
        options.executable('executable')
        options.environment = ['key': "12"]
        options.copyTo(target)

        then:
        target.getWorkingDir().asFile.get() == baseDir.absoluteFile
        target.getExecutable().get() == 'executable'
        target.getEnvironment().get() == [key: '12']
    }
}
