/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.nativebinaries.internal;

import org.gradle.api.internal.tasks.compile.CompileSpec;

import java.io.File;
import java.util.List;

public interface BinaryToolSpec extends CompileSpec {
    File getTempDir();

    void setTempDir(File tempDir);

    List<String> getArgs();

    void args(List<String> args);

    List<String> getSystemArgs();

    void systemArgs(List<String> args);

    List<String> getAllArgs();

}
