import gradlebuild.jvm.JvmCompileExtension

/*
 * Copyright 2018 the original author or authors.
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

plugins {
    id("java-base")
}

// Creates the `jvmCompile` extension, which is a container for JvmCompilation instances.
// JvmCompilation instances control the way each source set is compiled -- most importantly,
// it controls the JVM version of the compilation. This allows each source set within a project
// to compile to different JVM versions.

val jvmCompile = extensions.create<JvmCompileExtension>(JvmCompileExtension.NAME)

jvmCompile.compilations.configureEach {
    // Assume the compilation does not use any workarounds
    usesJdkInternals = false
    usesFutureStdlib = false

    // All compilations must have a target JVM version configured explicitly.
    targetJvmVersion = provider {
        throw RuntimeException("Target JVM version for $name has not been configured")
    }
}
