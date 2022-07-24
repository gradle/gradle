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
package org.gradle.nativeplatform.toolchain.internal;

import org.gradle.internal.operations.BuildOperation;
import org.gradle.internal.operations.logging.BuildOperationLogger;

import java.io.File;
import java.util.List;
import java.util.Map;

public interface CommandLineToolInvocation extends BuildOperation  {

    List<File> getPath();

    Map<String, String> getEnvironment();

    File getWorkDirectory();

    Iterable<String> getArgs();

    BuildOperationLogger getLogger();
}
