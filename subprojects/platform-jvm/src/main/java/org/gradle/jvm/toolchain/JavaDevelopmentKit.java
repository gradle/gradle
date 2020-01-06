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

package org.gradle.jvm.toolchain;

import org.gradle.api.Incubating;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFile;

/**
 * Information about a Java development kit.
 *
 * @since 6.2
 */
@Incubating
public interface JavaDevelopmentKit {
    /**
     * Returns the java compiler executable for this JDK.
     */
    RegularFile getJavacExecutable();

    /**
     * Returns the javadoc executable for this JDK.
     */
    RegularFile getJavadocExecutable();

    /**
     * Returns the classpath required to compile against the tools APIs of this JDK. This will be empty when no additional files are required.
     */
    FileCollection getToolsClasspath();
}
