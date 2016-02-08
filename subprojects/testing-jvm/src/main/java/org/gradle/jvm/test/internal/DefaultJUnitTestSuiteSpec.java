/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.jvm.test.internal;

import org.gradle.jvm.JvmComponentSpec;
import org.gradle.jvm.test.JUnitTestSuiteSpec;
import org.gradle.platform.base.DependencySpecContainer;
import org.gradle.platform.base.TransformationFileType;
import org.gradle.platform.base.internal.HasIntermediateOutputsComponentSpec;
import org.gradle.platform.base.internal.DefaultDependencySpecContainer;
import org.gradle.testing.base.internal.BaseTestSuiteSpec;

import java.util.Set;

import static org.gradle.jvm.internal.DefaultJvmLibrarySpec.defaultJvmComponentInputTypes;

public class DefaultJUnitTestSuiteSpec extends BaseTestSuiteSpec implements JUnitTestSuiteSpec, HasIntermediateOutputsComponentSpec {
    private String junitVersion;
    private final DependencySpecContainer dependencies = new DefaultDependencySpecContainer();

    @Override
    public String getjUnitVersion() {
        return junitVersion;
    }

    @Override
    public void setjUnitVersion(String junitVersion) {
        this.junitVersion = junitVersion;
    }

    @Override
    public DependencySpecContainer getDependencies() {
        return dependencies;
    }

    @Override
    public Set<? extends Class<? extends TransformationFileType>> getIntermediateTypes() {
        return defaultJvmComponentInputTypes();
    }

    @Override
    protected String getTypeName() {
        return "JUnit test suite";
    }

    @Override
    public JvmComponentSpec getTestedComponent() {
        return (JvmComponentSpec) super.getTestedComponent();
    }
}
