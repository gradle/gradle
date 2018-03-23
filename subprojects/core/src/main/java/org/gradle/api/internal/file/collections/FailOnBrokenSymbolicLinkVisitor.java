/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.file.collections;

import org.gradle.api.GradleException;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.file.ReproducibleFileVisitor;

public class FailOnBrokenSymbolicLinkVisitor implements DirectoryElementVisitor {
    private final FileVisitor visitor;

    public FailOnBrokenSymbolicLinkVisitor(FileVisitor visitor) {
        this.visitor = visitor;
    }

    public FileVisitor getDelegate() {
        return visitor;
    }

    @Override
    public void visitFile(FileVisitDetails details) {
        visitor.visitFile(details);
    }

    @Override
    public void visitDirectory(FileVisitDetails details) {
        visitor.visitDir(details);
    }

    @Override
    public void visitBrokenSymbolicLink(FileVisitDetails details) {
        throw new GradleException(String.format("Could not list contents of '%s'. Couldn't follow symbolic link.", details.getFile()));
    }

    @Override
    public boolean isReproducibleOrder() {
        return visitor instanceof ReproducibleFileVisitor && ((ReproducibleFileVisitor) visitor).isReproducibleFileOrder();
    }
}
