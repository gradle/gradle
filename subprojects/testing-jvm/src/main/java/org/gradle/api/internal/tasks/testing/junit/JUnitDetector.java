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
package org.gradle.api.internal.tasks.testing.junit;

import org.apache.commons.lang.StringUtils;
import org.gradle.api.internal.tasks.testing.detection.AbstractTestFrameworkDetector;
import org.gradle.api.internal.tasks.testing.detection.ClassFileExtractionManager;

import java.util.ArrayList;
import java.util.List;

public class JUnitDetector extends AbstractTestFrameworkDetector<JUnitTestClassDetector> {
    private static final String TEST_CASE = "junit/framework/TestCase";
    private static final String GROOVY_TEST_CASE = "groovy/util/GroovyTestCase";
    private final List<String> knownTestCaseClassNames;

    public JUnitDetector(ClassFileExtractionManager classFileExtractionManager) {
        super(classFileExtractionManager);
        this.knownTestCaseClassNames = new ArrayList<String>();
        addKnownTestCaseClassNames(TEST_CASE, GROOVY_TEST_CASE);
    }

    @Override
    protected JUnitTestClassDetector createClassVisitor() {
        return new JUnitTestClassDetector(this);
    }

    @Override
    protected boolean isKnownTestCaseClassName(String testCaseClassName) {
        boolean isKnownTestCase = false;

        if (StringUtils.isNotEmpty(testCaseClassName)) {
            isKnownTestCase = knownTestCaseClassNames.contains(testCaseClassName);
        }

        return isKnownTestCase;
    }

    private void addKnownTestCaseClassNames(String... knownTestCaseClassNames) {
        if (knownTestCaseClassNames != null && knownTestCaseClassNames.length != 0) {
            for (String knownTestCaseClassName : knownTestCaseClassNames) {
                if (StringUtils.isNotEmpty(knownTestCaseClassName)) {
                    this.knownTestCaseClassNames.add(knownTestCaseClassName.replaceAll("\\.", "/"));
                }
            }
        }
    }
}
