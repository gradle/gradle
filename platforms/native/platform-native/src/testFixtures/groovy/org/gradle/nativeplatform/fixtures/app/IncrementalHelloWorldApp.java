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

package org.gradle.nativeplatform.fixtures.app;

import org.gradle.integtests.fixtures.SourceFile;

import java.util.List;

public abstract class IncrementalHelloWorldApp extends CommonHeaderHelloWorldApp {
    public TestApp getAlternate() {
        return new TestApp() {
            @Override
            public SourceFile getMainSource() {
                return getAlternateMainSource();
            }

            @Override
            public SourceFile getLibraryHeader() {
                return getAlternateLibraryHeader();
            }

            @Override
            public List<SourceFile> getLibrarySources() {
                return getAlternateLibrarySources();
            }
        };
    }
    private SourceFile getAlternateLibraryHeader() {
        return getLibraryHeader();
    }

    public abstract SourceFile getAlternateMainSource();
    public abstract String getAlternateOutput();

    public abstract List<SourceFile> getAlternateLibrarySources();
    public abstract String getAlternateLibraryOutput();

    public abstract SourceFile getBrokenFile();
}
