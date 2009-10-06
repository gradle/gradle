/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.testing.detection;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Tom Eyckmans
 */
public class SetBuildingTestClassReceiver implements TestClassReceiver {

    private final Set<String> testClassNames;

    public SetBuildingTestClassReceiver(final Set<String> testClassNames) {
        this.testClassNames = new HashSet<String>(testClassNames);
    }

    public SetBuildingTestClassReceiver() {
        this.testClassNames = new HashSet<String>();
    }

    public void receiveTestClass(final String testClassName) {
        testClassNames.add(testClassName);
    }

    public Set<String> getTestClassNames() {
        return Collections.unmodifiableSet(testClassNames);
    }
}
