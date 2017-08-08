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

import java.util.Arrays;
import java.util.List;

/**
 * A C++ source file and corresponding header file.
 */
public abstract class CppSourceFileElement extends CppSourceElement {
    public abstract SourceFileElement getHeader();

    public abstract SourceFileElement getSource();

    @Override
    public SourceElement getHeaders() {
        return getHeader();
    }

    @Override
    public SourceElement getSources() {
        return getSource();
    }

    @Override
    public List<SourceFile> getFiles() {
        return Arrays.asList(getHeader().getSourceFile(), getSource().getSourceFile());
    }

    /**
     * Returns a copy of this element, with the header treated as a public library header
     */
    public CppSourceFileElement asLib() {
        return new CppSourceFileElement() {
            @Override
            public SourceFileElement getHeader() {
                return new SourceFileElement() {
                    @Override
                    public SourceFile getSourceFile() {
                        SourceFile header = CppSourceFileElement.this.getHeader().getSourceFile();
                        return sourceFile("public", header.getName(), header.getContent());
                    }
                };
            }

            @Override
            public SourceFileElement getSource() {
                return CppSourceFileElement.this.getSource();
            }
        };
    }
}
