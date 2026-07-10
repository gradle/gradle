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

import java.util.ArrayList;
import java.util.List;

/**
 * A C++ source file with optional public and private headers.
 */
public abstract class CppLibraryElement extends CppSourceElement {
    public abstract SourceElement getPublicHeaders();

    public SourceElement getPrivateHeaders() {
        return empty();
    }

    @Override
    public SourceElement getHeaders() {
        return ofElements(getPublicHeaders(), getPrivateHeaders());
    }

    /**
     * Returns a copy of this library with the public headers the 'public' headers directory.
     */
    public CppLibraryElement asLib() {
        final CppLibraryElement delegate = this;
        return new CppLibraryElement() {
            @Override
            public SourceElement getPublicHeaders() {
                List<SourceFile> headers = new ArrayList<SourceFile>();
                for (SourceFile sourceFile : delegate.getPublicHeaders().getFiles()) {
                    headers.add(sourceFile("public", sourceFile.getName(), sourceFile.getContent()));
                }
                return SourceElement.ofFiles(headers);
            }

            @Override
            public SourceElement getPrivateHeaders() {
                return delegate.getPrivateHeaders();
            }

            @Override
            public SourceElement getSources() {
                return delegate.getSources();
            }
        };
    }
}
