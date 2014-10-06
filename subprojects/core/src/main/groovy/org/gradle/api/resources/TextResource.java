/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.api.resources;

import org.gradle.api.Buildable;
import org.gradle.api.Incubating;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;

import java.io.File;
import java.io.Reader;

/**
 * A read-only body of text backed by a string, file, archive entry, or other source.
 * To create a text resource, use one of the factory methods in {@link ResourceHandler}
 * (e.g. {@code project.resources.text(myFile)}).
 *
 * @since 2.2
 */
@Incubating
public interface TextResource extends Buildable {
    /**
     * Returns a string containing the resource's text
     *
     * @return a string containing the resource's text
     */
    String asString();

    /**
     * Returns a reader that allows to read the resource's text.
     *
     * @return a reader that allows to read the resource's text
     */
    Reader asReader();


    /**
     * Returns a file containing the resource's text. If this resource is backed by a file,
     * that file will be returned. If this resource is backed by an archive entry, a
     * temporary file created by extracting the entry will be returned. Otherwise,
     * a temporary file using the platform's default character encoding will be returned.
     *
     * @return a file containing the resource's text
     */
    File asFile();

    /**
     * Returns the input properties registered when this resource is used as task input.
     * Not typically used directly.
     *
     * @return the input properties registered when this resource is used as task input
     */
    @Input
    @Optional
    Object getInputProperties();

    /**
     * Returns the input files registered when this resource is used as task input.
     * Not typically used directly.
     *
     * @return the input files registered when this resource is used as task input
     */
    @InputFiles
    @Optional
    Object getInputFiles();
}
