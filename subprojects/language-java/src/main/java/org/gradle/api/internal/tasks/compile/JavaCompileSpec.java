/*
 * Copyright 2012 the original author or authors.
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

import org.gradle.api.tasks.compile.CompileOptions;

import java.io.File;
import java.util.List;

public interface JavaCompileSpec extends JvmLanguageCompileSpec {
    CompileOptions getCompileOptions();

    @Override
    File getDestinationDir();

    /**
     * The annotation processor path to use. When empty, no processing should be done. When not empty, processing should be done.
     */
    List<File> getAnnotationProcessorPath();

    void setAnnotationProcessorPath(List<File> path);

    /**
     * Whether or not the {@link javax.tools.JavaCompiler} should allow the following two things.
     * <ul>
     *     <li>Specifying a <code>-sourcepath</code> command line argument.</li>
     *     <li>Find implicit sources on the sourcepath.</li>
     * </ul>
     * <p>
     * If this is false for a spec and an empty <code>-sourcepath</code> argument to the compiler,
     * and silently remove any <code>-sourcepath</code> options and the argument to that option from
     * {@link CompileOptions#getCompilerArgs()}.
     * <p>
     * For Java 9 compilation, the default behavior is to omit the <code>-sourcepath</code> element entirely when
     * there is a <code>module-info.java</code> in the {@link JvmLanguageCompileSpec#getSource()} file collection.
     */
    boolean respectsSourcepath();
}
