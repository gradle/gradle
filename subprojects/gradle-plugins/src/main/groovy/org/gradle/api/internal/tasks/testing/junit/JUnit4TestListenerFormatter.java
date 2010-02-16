/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.junit;

import junit.framework.Test;
import org.gradle.api.internal.tasks.testing.DefaultTest;
import org.gradle.api.internal.tasks.testing.TestInternal;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.util.TimeProvider;
import org.junit.runner.Describable;
import org.junit.runner.Description;

public class JUnit4TestListenerFormatter extends TestListenerFormatter {
    public JUnit4TestListenerFormatter(TestResultProcessor resultProcessor, TimeProvider timeProvider) {
        super(resultProcessor, timeProvider);
    }

    @Override
    protected TestInternal convert(Long id, Test test) {
        if (test instanceof Describable) {
            Describable describable = (Describable) test;
            Description description = describable.getDescription();
            return new DefaultTest(id, description.getClassName(), description.getMethodName());
        }
        return super.convert(id, test);
    }
}
