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

import org.gradle.api.internal.resources.ResourceIsAFolderException;
import org.gradle.api.resources.MissingResourceException;

import java.io.*;

public class FileResource extends AbstractFileResource {

    public FileResource(File file) {
        super(file);
    }

    public InputStream read() {
        if (file.isDirectory()) {
            throw new ResourceIsAFolderException(String.format("Cannot read resource %s because it is a folder.", file.getName()));
        }
        try {
            FileInputStream fis = new FileInputStream(file);
            return new BufferedInputStream(fis);
        } catch (FileNotFoundException e) {
            throw new MissingResourceException(String.format("Cannot read the file resource because the file %s does not exist", file.getName()));
        }
    }
}
