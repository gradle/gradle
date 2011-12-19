/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.tooling.model;

import java.io.File;
import java.util.List;

/**
 * Informs about the build environment, like Gradle version or jvm in use
 * <p>
 * Marked as deprecated because the API is not yet confirmed.
 * E.g. we will provide this information for sure, we just haven't yet confirmed the API.
 */
@Deprecated
public interface BuildEnvironment extends Element {

    String getGradleVersion();

    File getJavaHome();

    List<String> getJvmArguments();
}
