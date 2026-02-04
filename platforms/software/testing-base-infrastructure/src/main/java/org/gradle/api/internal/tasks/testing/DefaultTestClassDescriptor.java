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

package org.gradle.api.internal.tasks.testing;

import org.gradle.api.internal.tasks.testing.source.DefaultClassSource;
import org.gradle.api.tasks.testing.source.TestSource;
import org.gradle.internal.scan.UsedByScanPlugin;
import org.jspecify.annotations.Nullable;

/**
 * Note that as of Gradle 9.3, this class is also used for non-class-based testing and may not
 * represent a class at all.
 * <p>
 * Thus {@link #getClassName()} and {@link #getClassDisplayName()} may return identifiers
 * that are <strong>NOT</strong> class names.
 */
@UsedByScanPlugin("test-distribution")
public class DefaultTestClassDescriptor extends DefaultTestSuiteDescriptor {
    private final String classDisplayName;

    public DefaultTestClassDescriptor(Object id, String className) {
        this(id, className, null);
    }

    @UsedByScanPlugin("test-distribution")
    public DefaultTestClassDescriptor(Object id, String className, @Nullable String classDisplayName) {
        this(id, className, classDisplayName, new DefaultClassSource(className));
    }

    public DefaultTestClassDescriptor(Object id, String className, @Nullable String classDisplayName, TestSource source) {
        super(id, className, source);
        this.classDisplayName = classDisplayName == null ? className : classDisplayName;
    }

    @Override
    public String getClassName() {
        return getName();
    }

    @Override
    public String getDisplayName() {
        return getClassDisplayName();
    }

    @Override
    public String getClassDisplayName() {
        return classDisplayName;
    }

    @Override
    public String toString() {
        return "Test class " + getClassName();
    }
}
