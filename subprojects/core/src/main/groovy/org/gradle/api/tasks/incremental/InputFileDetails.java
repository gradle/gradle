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
package org.gradle.api.tasks.incremental;

import org.gradle.api.Incubating;

import java.io.File;

/**
 * A change to an input file.
 */
@Incubating
public interface InputFileDetails {
    /**
     * Was the file added?
     * @return true if the file was added since the last execution
     */
    boolean isAdded();

    /**
     * Was the file modified?
     * @return if the file was modified
     */
    boolean isModified();

    /**
     * Was the file removed?
     * @return true if the file was removed since the last execution
     */
    boolean isRemoved();

    /**
     * The input file, which may no longer exist.
     * @return the input file
     */
    File getFile();
}
