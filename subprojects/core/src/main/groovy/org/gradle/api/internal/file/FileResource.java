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

package org.gradle.api.internal.file;

import org.gradle.api.internal.DescribedReadableResource;
import org.gradle.api.resources.ResourceDoesNotExist;
import org.gradle.api.resources.ResourceIsAFolder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

/**
 * by Szczepan Faber, created at: 11/22/11
 */
public class FileResource extends AbstractFileResource implements DescribedReadableResource {

    public FileResource(File file) {
        super(file);
    }

    public InputStream read() {
        if (file.isDirectory()) {
            throw new ResourceIsAFolder(String.format("Cannot read from file resource because %s is a folder.", file.getName()));
        }
        try {
            return new FileInputStream(file);
        } catch (FileNotFoundException e) {
            throw new ResourceDoesNotExist(String.format("Cannot read from file resource because the file %s does not exist", file.getName()));
        }
    }
}