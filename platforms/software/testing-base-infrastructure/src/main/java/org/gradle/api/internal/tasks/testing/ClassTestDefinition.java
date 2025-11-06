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
package org.gradle.api.internal.tasks.testing;

import org.apache.commons.lang3.StringUtils;
import org.gradle.api.internal.tasks.testing.filter.TestSelectionMatcher;
import org.jspecify.annotations.NullMarked;

/**
 * A test definition which denotes a single test class.
 */
@NullMarked
public final class ClassTestDefinition implements TestDefinition {
    private final String testClassName;

    public ClassTestDefinition(String testClassName) {
        if (StringUtils.isEmpty(testClassName)) {
            throw new IllegalArgumentException("testClassName is empty!");
        }

        this.testClassName = testClassName;
    }

    public String getTestClassName() {
        return testClassName;
    }

    @Override
    public String getDisplayName() {
        return "test class '" + testClassName + "'";
    }

    /**
     * {@inheritDoc}
     * @implNote Returns the name of the test class.
     */
    @Override
    public String getId() {
        return getTestClassName();
    }

    @Override
    public boolean matches(TestSelectionMatcher matcher) {
        return matcher.mayIncludeClass(getId());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ClassTestDefinition that = (ClassTestDefinition) o;

        return testClassName.equals(that.testClassName);
    }

    @Override
    public int hashCode() {
        return testClassName.hashCode();
    }

    @Override
    public String toString() {
        return "ClassTestDefinition(" + getId() + ')';
    }
}
