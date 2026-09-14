/*
 * Copyright 2025 the original author or authors.
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

import org.gradle.api.internal.file.TestFiles
import org.gradle.initialization.BuildCancellationToken
import org.gradle.internal.jvm.Jvm
import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.internal.GUtil
import org.gradle.util.internal.TextUtil
import org.junit.Rule

import java.util.concurrent.Executor

abstract class AbstractExecHandleSpec extends ConcurrentSpec {
    @Rule
    final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    protected BuildCancellationToken buildCancellationToken = Mock(BuildCancellationToken)

    ClientExecHandleBuilder handle() {
        handle(executor)
    }

    ClientExecHandleBuilder handle(Executor exec) {
        new DefaultClientExecHandleBuilder(TestFiles.pathToFileResolver(), exec, buildCancellationToken)
            .setExecutable(Jvm.current().getJavaExecutable().getAbsolutePath())
            .setTimeout(20000) //sanity timeout
            .setWorkingDir(tmpDir.getTestDirectory())
            .environment('CLASSPATH', mergeClasspath())
            .environment('JAVA_EXE_PATH', TextUtil.normaliseFileSeparators(Jvm.current().getJavaExecutable().getAbsolutePath()))
    }

    String mergeClasspath() {
        if (System.getenv('CLASSPATH') == null) {
            return System.getProperty('java.class.path')
        }
        "${System.getenv('CLASSPATH')}${File.pathSeparator}${System.getProperty('java.class.path')}"
    }

    List args(Class mainClass, String... args) {
        GUtil.flattenElements(mainClass.getName(), args)
    }
}
