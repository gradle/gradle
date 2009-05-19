/*
 * Copyright 2008 the original author or authors.
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
package org.gradle.api.testing;

import org.gradle.api.Project;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.testing.AbstractTestFrameworkOptions;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.Collection;

/**
 * @author Tom Eyckmans
 */
public interface TestFramework {
    void initialize(Project project, Test testTask);

    void prepare(Project project, Test testTask);

    void execute(Project project, Test testTask, Collection<String> includes, Collection<String> excludes);

    void report(Project project, Test testTask);

    AbstractTestFrameworkOptions getOptions();

    boolean isTestClass(File testClassFile);

    Set<String> getTestClassNames();
}
