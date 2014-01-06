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

import org.gradle.nativebinaries.test.TestSuite;
import org.gradle.nativebinaries.internal.AbstractProjectNativeComponent;
import org.gradle.nativebinaries.internal.NativeProjectComponentIdentifier;

public class DefaultTestSuite extends AbstractProjectNativeComponent implements TestSuite {
    public DefaultTestSuite(NativeProjectComponentIdentifier id) {
        super(id);
    }

    public String getDisplayName() {
        return String.format("unit tests '%s'", getName());
    }
}