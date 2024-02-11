/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.java.compile.jpms

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.jvm.JavaCompileMultiTestInterceptor
import org.gradle.integtests.fixtures.jvm.JavaCompileTest
import org.gradle.util.internal.TextUtil

@JavaCompileTest
abstract class AbstractJavaModuleCompileIntegrationTest extends AbstractJavaModuleIntegrationTest {

    def setup() {
        switch (JavaCompileMultiTestInterceptor.compiler) {
            case JavaCompileMultiTestInterceptor.Compiler.WORKER_JDK_COMPILER:
                buildFile << """
                    tasks.withType(JavaCompile) {
                        options.fork = true
                    }
                """
                break
            case JavaCompileMultiTestInterceptor.Compiler.WORKER_COMMAND_LINE_COMPILER:
                def javaHome = TextUtil.escapeString(AvailableJavaHomes.getJdk(JavaVersion.current()).javaHome.absolutePath)
                buildFile << """
                    tasks.withType(JavaCompile) {
                        options.fork = true
                        options.forkOptions.javaHome = file('$javaHome')
                    }
                """
                break
        }
    }
}
