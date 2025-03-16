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
package org.gradle.testing.jacoco.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Classpath;
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty;
import org.gradle.work.DisableCachingByDefault;

/**
 * Base class for Jacoco tasks.
 */
@DisableCachingByDefault(because = "Abstract super-class, not to be instantiated directly")
public abstract class JacocoBase extends DefaultTask {

    private FileCollection jacocoClasspath;

    /**
     * Classpath containing Jacoco classes for use by the task.
     */
    @Classpath
    @ToBeReplacedByLazyProperty
    public FileCollection getJacocoClasspath() {
        return jacocoClasspath;
    }

    public void setJacocoClasspath(FileCollection jacocoClasspath) {
        this.jacocoClasspath = jacocoClasspath;
    }
}
