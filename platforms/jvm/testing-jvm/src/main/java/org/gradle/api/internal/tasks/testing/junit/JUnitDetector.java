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

import com.google.common.collect.ImmutableSet;
import org.gradle.api.internal.tasks.testing.detection.AbstractTestFrameworkDetector;
import org.gradle.api.internal.tasks.testing.detection.ClassFileExtractionManager;

public class JUnitDetector extends AbstractTestFrameworkDetector<JUnitTestClassDetector> {
    private static final String TEST_CASE = "junit/framework/TestCase";
    private static final String GROOVY_LEGACY_TEST_CASE = "groovy/util/GroovyTestCase";
    /**
     * groovy.util.GroovyTestCase was renamed to groovy.test.GroovyTestCase in the groovy-test
     * subproject to prevent split packages in Groovy 4.  See <a href="https://issues.apache.org/jira/browse/GROOVY-8647">GROOVY-8647</a>
     */
    private static final String GROOVY_TEST_CASE = "groovy/test/GroovyTestCase";
    private static final ImmutableSet<String> KNOWN_TEST_CASE_CLASS_NAMES = ImmutableSet.of(TEST_CASE, GROOVY_LEGACY_TEST_CASE, GROOVY_TEST_CASE);

    public JUnitDetector(ClassFileExtractionManager classFileExtractionManager) {
        super(classFileExtractionManager);
    }

    @Override
    protected JUnitTestClassDetector createClassVisitor() {
        return new JUnitTestClassDetector(this);
    }

    @Override
    protected boolean isKnownTestCaseClassName(String testCaseClassName) {
        return KNOWN_TEST_CASE_CLASS_NAMES.contains(testCaseClassName);
    }
}
