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

package org.gradle.api.internal.tasks.compile;

import java.io.File;

/**
 * Describes a Java compilation where the compiler is executed in a separate process.
 */
public class DefaultForkingJavaCompileSpec extends DefaultJavaCompileSpec implements ForkingJavaCompileSpec {

    private final File javaHome;
    private final int javaLanguageVersion;

    public DefaultForkingJavaCompileSpec(File javaHome, int javaLanguageVersion) {
        this.javaHome = javaHome;
        this.javaLanguageVersion = javaLanguageVersion;
    }

    @Override
    public File getJavaHome() {
        return javaHome;
    }

    @Override
    public int getJavaLanguageVersion() {
        return javaLanguageVersion;
    }

}
