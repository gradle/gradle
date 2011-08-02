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

package org.gradle.tooling.internal.idea;

import org.gradle.tooling.model.idea.IdeaContentRoot;

import java.io.File;
import java.io.Serializable;

/**
 * @author: Szczepan Faber, created at: 8/3/11
 */
public class DefaultIdeaContentRoot implements IdeaContentRoot, Serializable {

    File rootDirectory;

    public DefaultIdeaContentRoot(File rootDirectory) {
        this.rootDirectory = rootDirectory;
    }

    public File getRootDirectory() {
        return rootDirectory;
    }

    public void setRootDirectory(File rootDirectory) {
        this.rootDirectory = rootDirectory;
    }

    @Override
    public String toString() {
        return "IdeaContentRoot{"
                + "rootDirectory=" + rootDirectory
                + '}';
    }
}