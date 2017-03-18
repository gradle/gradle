/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.play.internal.toolchain

import org.gradle.api.internal.file.FileResolver
import org.gradle.api.tasks.compile.BaseForkOptions
import org.gradle.language.base.internal.compile.Compiler
import org.gradle.play.internal.spec.PlayCompileSpec
import org.gradle.workers.WorkerExecutor
import org.gradle.workers.internal.DefaultWorkerConfiguration
import spock.lang.Specification

class DaemonPlayCompilerTest extends Specification {

    def delegate = Mock(Compiler)
    def workerExecutor = Mock(WorkerExecutor)
    def spec = Mock(PlayCompileSpec)
    def forkOptions = Mock(BaseForkOptions)

    def setup() {
        _ * spec.getForkOptions() >> forkOptions
        _ * forkOptions.getJvmArgs() >> []
    }

    def "passes compile classpath and packages to worker daemon configuration"() {
        given:
        def classpath = someClasspath()
        def packages = ["foo", "bar"]
        def compiler = new DaemonPlayCompiler(delegate, workerExecutor, classpath, packages)
        when:
        def config = new DefaultWorkerConfiguration(Mock(FileResolver))
        compiler.applyWorkerConfiguration(spec, config)
        then:
        config.getClasspath() == classpath
        config.getSharedPackages() == packages
    }

    def "applies fork settings to worker daemon configuration"() {
        given:
        def compiler = new DaemonPlayCompiler(delegate, workerExecutor, someClasspath(), [])
        when:
        1 * forkOptions.getMemoryInitialSize() >> "256m"
        1 * forkOptions.getMemoryMaximumSize() >> "512m"
        then:
        def config = new DefaultWorkerConfiguration(Mock(FileResolver))
        compiler.applyWorkerConfiguration(spec, config)
        config.forkOptions.getMinHeapSize() == "256m"
        config.forkOptions.getMaxHeapSize() == "512m"
    }

    def someClasspath() {
        [Mock(File), Mock(File)]
    }
}
