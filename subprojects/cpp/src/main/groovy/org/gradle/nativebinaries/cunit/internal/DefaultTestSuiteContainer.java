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

package org.gradle.nativebinaries.cunit.internal;

import org.gradle.api.Project;
import org.gradle.api.internal.AbstractNamedDomainObjectContainer;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.nativebinaries.cunit.TestSuite;
import org.gradle.nativebinaries.cunit.TestSuiteContainer;
import org.gradle.nativebinaries.internal.NativeProjectComponentIdentifier;

// TODO:DAZ Add a 'components' container (polymorphic), and then a writable container filtered by type that looks like a non-polymorphic container
// Then 'executables', 'libraries' and 'testSuites' would all be filtered containers, not separate.
public class DefaultTestSuiteContainer extends AbstractNamedDomainObjectContainer<TestSuite> implements TestSuiteContainer {
    private final Project project;

    public DefaultTestSuiteContainer(Instantiator instantiator, Project project) {
        super(TestSuite.class, instantiator);
        this.project = project;
    }

    @Override
    protected TestSuite doCreate(String name) {
        NativeProjectComponentIdentifier id = new NativeProjectComponentIdentifier(project.getPath(), name);
        return getInstantiator().newInstance(DefaultTestSuite.class, id);
    }
}
