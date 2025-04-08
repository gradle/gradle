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
package org.gradle.api.internal.tasks.util

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.process.ProcessForkOptions
import org.gradle.process.internal.DefaultProcessForkOptions
import spock.lang.Specification

class DefaultProcessForkOptionsTest extends Specification {
    def baseDir = new File("base-dir")
    def resolver = Mock(FileResolver.class) {
        resolve(".") >> baseDir
    }
    def workingDir = Mock(DirectoryProperty) {
        getAsFile() >> Mock(Provider)
        convention(_) >> it
    }
    def objectFactory = Mock(ObjectFactory.class) {
        directoryProperty() >> workingDir
    }
    def options = new DefaultProcessForkOptions(objectFactory, resolver)

    def defaultValues() {
        expect:
        options.executable == null
        !options.environment.empty
    }

    def resolvesWorkingDirectoryOnGet() {
        when:
        options.workingDir = 12
        options.workingDir == baseDir

        then:
        1 * resolver.resolve(12) >> baseDir
        1 * workingDir.set(baseDir)
        1 * workingDir.getAsFile().get() >> baseDir
    }

    def convertsEnvironmentToString() {
        when:
        options.environment = [key1: 12, key2: "${1+2}", key3: null]

        then:
        options.actualEnvironment == [key1: '12', key2: '3', key3: 'null']
    }

    def canAddEnvironmentVariables() {
        when:
        options.environment = [:]

        then:
        options.environment == [:]

        when:
        options.environment('key', 12)

        then:
        options.environment == [key: 12]
        options.actualEnvironment == [key: '12']

        when:
        options.environment(key2: "value")

        then:
        options.environment == [key: 12, key2: "value"]
    }

    def canCopyToTargetOptions() {
        given:
        def target = Mock(ProcessForkOptions)

        when:
        options.executable('executable')
        options.environment('key', 12)
        options.copyTo(target)

        then:
        1 * workingDir.getAsFile().get() >> baseDir
        1 * target.setWorkingDir(baseDir)
        1 * target.setExecutable('executable')
        1 * target.setEnvironment({ !it.empty })
    }
}
