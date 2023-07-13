/*
 * Copyright 2017 the original author or authors.
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


package org.gradle.java.compile

import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.util.internal.TextUtil

class CommandLineJavaCompilerIntegrationTest extends JavaCompilerIntegrationSpec {

    @Override
    String compilerConfiguration() {
        def javaHomePath = AvailableJavaHomes.jdk11.javaHome.toString()
        """
            compileJava.options.with {
                fork = true
                forkOptions.javaHome = file("${TextUtil.normaliseFileSeparators(javaHomePath)}")
            }
        """
    }

    @Override
    String logStatement() {
        "Compiling with Java command line compiler"
    }
}
