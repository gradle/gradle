/*
 * Copyright 2007-2009 the original author or authors.
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
package org.gradle.api.artifacts;

import java.io.File;

/**
 * Instruction about uploading details for artifacts produced by a project.
 *
 * @author Hans Dockter
 */
public class PublishInstruction {
    private File ivyFileParentDir = null;

    /**
     * Returns if an ivy.xml file is uploaded or not. This is a convenient function which returns
     * true if the ivy file parent dir is different to null and false otherwise.
     *
     * @see #setIvyFileParentDir(java.io.File) 
     */
    public boolean isUploadModuleDescriptor() {
        return ivyFileParentDir != null;
    }

    /**
     * Returns the directory where to find the ivy.xml file. Can be null.
     *
     * @see #setIvyFileParentDir(java.io.File)
     */
    public File getIvyFileParentDir() {
        return ivyFileParentDir;
    }

    /**
     * Sets the directory where to find the ivy.xml file. If set to null, no ivy.xml file is uploaded.
     *
     * @param ivyFileParentDir the directory where to find the ivy.xml file.
     */
    public void setIvyFileParentDir(File ivyFileParentDir) {
        this.ivyFileParentDir = ivyFileParentDir;
    }
}
