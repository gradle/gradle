/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.files;

import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileManager;
import javax.tools.StandardLocation;

/**
 * Pretends not to know about the <code>sourcepath</code>.
 */
public class SourcepathIgnoringJavaFileManager extends ForwardingJavaFileManager<JavaFileManager> {

    public SourcepathIgnoringJavaFileManager(JavaFileManager fileManager) {
        super(fileManager);
    }

    /**
     * There is currently a requirement in the JDK9 javac implementation
     * that when javac is invoked with an explicitly empty sourcepath
     * (i.e. {@code --sourcepath ""}), it won't allow you to compile a java 9
     * module. However, we really want to explicitly set an empty sourcepath
     * so that we don't implicitly pull in unrequested sourcefiles which
     * haven't been snapshotted because we will consider the task up-to-date
     * if the implicit files change.
     * <p>
     * This implementation of hasLocation() pretends that the JavaFileManager
     * has no concept of a source path.
     */
    public boolean hasLocation(Location location) {
        return !location.equals(StandardLocation.SOURCE_PATH) && fileManager.hasLocation(location);
    }
}
