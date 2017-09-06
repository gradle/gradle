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

import com.google.common.collect.Sets;
import org.gradle.api.Transformer;
import org.gradle.integtests.fixtures.SourceFile;
import org.gradle.util.CollectionUtils;

import java.util.Set;

public abstract class IncrementalSwiftElement extends IncrementalElement {
    @Override
    protected Set<String> intermediateFilenames(SourceFile sourceFile) {
        final String name = sourceFile.getName().replace(".swift", "");
        return CollectionUtils.collect(Sets.newHashSet(".o", "~partial.swiftdoc", "~partial.swiftmodule"), new Transformer<String, String>() {
            @Override
            public String transform(String suffix) {
                return name + suffix;
            }
        });
    }

    @Override
    public Set<String> getExpectedIntermediateFilenames() {
        return appendGenericExpectedIntermediateFilenames(super.getExpectedIntermediateFilenames());
    }

    @Override
    public Set<String> getExpectedAlternateIntermediateFilenames() {
        return appendGenericExpectedIntermediateFilenames(super.getExpectedAlternateIntermediateFilenames());
    }

    private Set<String> appendGenericExpectedIntermediateFilenames(Set<String> delegate) {
        delegate.add("output-file-map.json");
        delegate.add(getModuleName() + ".swiftmodule");
        delegate.add(getModuleName() + ".swiftdoc");
        return delegate;
    }

    public abstract String getModuleName();
}
