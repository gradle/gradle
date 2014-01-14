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

package org.gradle.nativebinaries.test.internal;

import org.gradle.api.internal.DefaultPolymorphicDomainObjectContainer;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.nativebinaries.test.TestSuite;
import org.gradle.nativebinaries.test.TestSuiteContainer;

// TODO:DAZ Add a 'components' container (polymorphic), and then a writable container filtered by type that looks like a non-polymorphic container
// Then 'executables', 'libraries' and 'testSuites' would all be filtered containers, not separate.
public class DefaultTestSuiteContainer extends DefaultPolymorphicDomainObjectContainer<TestSuite> implements TestSuiteContainer {
    public DefaultTestSuiteContainer(Instantiator instantiator) {
        super(TestSuite.class, instantiator);
    }
}
