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

package org.gradle.nativeplatform.test.xctest.internal;

import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFileVar;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.language.swift.internal.DefaultSwiftComponent;
import org.gradle.nativeplatform.test.xctest.SwiftXCTestBundle;

import javax.inject.Inject;

public class DefaultSwiftXCTestBundle extends DefaultSwiftComponent implements SwiftXCTestBundle {
    private final RegularFileVar informationPropertyList;

    @Inject
    public DefaultSwiftXCTestBundle(String name, FileOperations fileOperations, ProviderFactory providerFactory, ProjectLayout projectLayout) {
        super(name, fileOperations, providerFactory);

        informationPropertyList = projectLayout.newFileVar();
        informationPropertyList.set(projectLayout.getProjectDirectory().file("src/" + name + "/resources/Info.plist"));
    }

    @Override
    public RegularFileVar getInformationPropertyList() {
        return informationPropertyList;
    }
}
