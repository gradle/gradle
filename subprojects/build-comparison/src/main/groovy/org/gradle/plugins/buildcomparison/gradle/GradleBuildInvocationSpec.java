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

package org.gradle.plugins.buildcomparison.gradle;

import java.io.File;
import java.util.List;

/**
 * A specification for launching a Gradle build with any Gradle version.
  */
public interface GradleBuildInvocationSpec {

    File getProjectDir();

    void setProjectDir(Object projectDir);

    String getGradleVersion();

    void setGradleVersion(String gradleVersion);

    List<String> getTasks();

    void setTasks(Iterable<String> tasks);

    List<String> getArguments();

    void setArguments(Iterable<String> arguments);

}
