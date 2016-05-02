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
package org.gradle.api.internal.file.copy;

import org.gradle.api.Action;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.file.CopyActionProcessingStreamAction;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.reflect.Instantiator;

public class CopySpecActionImpl implements Action<CopySpecResolver> {
    private final CopyActionProcessingStreamAction action;
    private final Instantiator instantiator;
    private final FileSystem fileSystem;

    public CopySpecActionImpl(CopyActionProcessingStreamAction action, Instantiator instantiator, FileSystem fileSystem) {
        this.action = action;
        this.instantiator = instantiator;
        this.fileSystem = fileSystem;
    }

    public void execute(final CopySpecResolver specResolver) {
        FileTree source = specResolver.getSource();
        source.visit(new CopyFileVisitorImpl(specResolver, action, instantiator, fileSystem));
    }
}