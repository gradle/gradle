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

public abstract class IncrementalSwiftElement extends IncrementalElement {
    @Override
    protected List<String> toExpectedIntermediateDescendants(SourceElement sourceElement) {
        List<String> result = new ArrayList<String>();

        String sourceSetName = sourceElement.getSourceSetName();
        for (SourceFile sourceFile : sourceElement.getFiles()) {
            result.add(getIntermediateRelativeFilePath(sourceSetName, sourceFile, ".o"));
            result.add(getIntermediateRelativeFilePath(sourceSetName, sourceFile, "~partial.swiftdoc"));
            result.add(getIntermediateRelativeFilePath(sourceSetName, sourceFile, "~partial.swiftmodule"));
        }
        return result;
    }

    @Override
    public List<String> getExpectedIntermediateDescendants() {
        return appendGenericExpectedIntermediateDescendants(super.getExpectedIntermediateDescendants());
    }

    @Override
    public List<String> getExpectedAlternateIntermediateDescendants() {
        return appendGenericExpectedIntermediateDescendants(super.getExpectedAlternateIntermediateDescendants());
    }

    private List<String> appendGenericExpectedIntermediateDescendants(List<String> delegate) {
        delegate.add("output-file-map.json");
        delegate.add(getModuleName() + ".swiftmodule");
        delegate.add(getModuleName() + ".swiftdoc");
        return delegate;
    }

    public abstract String getModuleName();
}
