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

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.gradle.integtests.fixtures.SourceFile;
import org.gradle.test.fixtures.file.TestFile;
import org.gradle.util.GUtil;

import java.util.List;

public abstract class MainWithXCTestSourceElement extends XCTestSourceElement {
    public abstract SwiftSourceElement getMain();
    public abstract XCTestSourceElement getTest();

    @Override
    public List<XCTestSourceFileElement> getTestSuites() {
        return getTest().getTestSuites();
    }

    @Override
    public List<SourceFile> getFiles() {
        return Lists.newArrayList(Iterables.concat(getMain().getFiles(), getTest().getFiles()));
    }

    @Override
    public void writeToProject(TestFile projectDir) {
        getMain().writeToProject(projectDir);
        getTest().writeToProject(projectDir);
    }

    public MainWithXCTestSourceElement inProject(final String name) {
        final MainWithXCTestSourceElement delegate = this;
        final String mainModuleName = GUtil.toCamelCase(name);
        return new MainWithXCTestSourceElement() {
            @Override
            public SwiftSourceElement getMain() {
                return delegate.getMain().asModule(mainModuleName);
            }

            @Override
            public XCTestSourceElement getTest() {
                return delegate.getTest().asModule(GUtil.toCamelCase(name + "Test")).withImport(mainModuleName);
            }

            @Override
            public String getModuleName() {
                return delegate.getModuleName();
            }
        };
    }

    @Override
    public MainWithXCTestSourceElement withInfoPlist() {
        final MainWithXCTestSourceElement delegate = this;
        return new MainWithXCTestSourceElement() {
            @Override
            public SwiftSourceElement getMain() {
                return delegate.getMain();
            }

            @Override
            public XCTestSourceElement getTest() {
                return delegate.getTest().withInfoPlist();
            }

            @Override
            public String getModuleName() {
                return delegate.getModuleName();
            }
        };
    }
}
