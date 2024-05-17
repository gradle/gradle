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

package org.gradle.nativeplatform.fixtures.app;

import org.gradle.integtests.fixtures.SourceFile;

import java.util.Collections;
import java.util.List;

/**
 * A single source file.
 */
public abstract class SourceFileElement extends SourceElement {
    public abstract SourceFile getSourceFile();

    @Override
    public List<SourceFile> getFiles() {
        return Collections.singletonList(getSourceFile());
    }

    public static SourceFileElement ofFile(final SourceFile file) {
        return new SourceFileElement() {
            @Override
            public SourceFile getSourceFile() {
                return file;
            }
        };
    }
}
