/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.api.InvalidUserDataException;

import java.io.File;

/**
 * Uploading details for artifacts produced by a project.
 *
 * @author Hans Dockter
 */
public class PublishInstruction {
    private boolean uploadDescriptor;
    private File descriptorDestination;

    /**
     * Creates a publish instruction for not uploading an module descriptor file.
     */
    public PublishInstruction() {
        uploadDescriptor = false;
        descriptorDestination = null;
    }

    /**
     * Creates a publish instruction. If <code>uploadDescriptor</code> is set to true and the target destination
     * is an ivy repository, the ivy file destination needs to be specified. 
     *
     * @param uploadDescriptor
     * @param descriptorDestination
     */
    public PublishInstruction(boolean uploadDescriptor, File descriptorDestination) {
        if (uploadDescriptor && descriptorDestination == null) {
            throw new InvalidUserDataException("You must specify a module descriptor destination, if a module descriptor should be uploaded.");
        }
        if (!uploadDescriptor && descriptorDestination != null) {
            throw new InvalidUserDataException("You must not specify a module descriptor destination, if a module descriptor should not be uploaded.");
        }
        this.uploadDescriptor = uploadDescriptor;
        this.descriptorDestination = descriptorDestination;
    }

    /**
     * Returns whether an xml module descriptor file should be uploaded or not. 
     */
    public boolean isUploadDescriptor() {
        return uploadDescriptor;
    }

    /**
     * Returns the file destination where to create the ivy.xml file. Can be null.
     */
    public File getDescriptorDestination() {
        return descriptorDestination;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        PublishInstruction that = (PublishInstruction) o;

        if (uploadDescriptor != that.uploadDescriptor) {
            return false;
        }
        if (descriptorDestination != null ? !descriptorDestination.equals(that.descriptorDestination)
                : that.descriptorDestination != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = uploadDescriptor ? 1 : 0;
        result = 31 * result + (descriptorDestination != null ? descriptorDestination.hashCode() : 0);
        return result;
    }
}
