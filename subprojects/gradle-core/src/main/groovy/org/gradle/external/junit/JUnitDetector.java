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

package org.gradle.external.junit;

import org.gradle.api.testing.execution.AbstractTestFrameworkDetector;
import org.gradle.api.testing.execution.TestClassVisitor;
import org.gradle.api.GradleException;
import org.objectweb.asm.ClassReader;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.List;

/**
 * @author Tom Eyckmans
 */
public class JUnitDetector extends AbstractTestFrameworkDetector<JUnitTestClassDetecter> {
    private static final Logger logger = LoggerFactory.getLogger(JUnitDetector.class);

    public JUnitDetector(File testClassesDirectory, List<File> testClasspath) {
        super(testClassesDirectory, testClasspath);
    }

    protected JUnitTestClassDetecter createClassVisitor() {
        return new JUnitTestClassDetecter(this);
    }

    public boolean processPossibleTestClass(File testClassFile) {
        final TestClassVisitor classVisitor = createClassVisitor();

        InputStream classStream = null;
        try {
            classStream = new BufferedInputStream(new FileInputStream(testClassFile));
            final ClassReader classReader = new ClassReader(classStream);
            classReader.accept(classVisitor, true);
        }
        catch ( Throwable e ) {
            throw new GradleException("failed to read class file " + testClassFile.getAbsolutePath(), e);
        }
        finally {
            IOUtils.closeQuietly(classStream);
        }

        boolean isTest = classVisitor.isTest();

        if (!isTest) {
            final String superClassName = classVisitor.getSuperClassName();
            if ( superClassName.startsWith("java/lang") ||
                 superClassName.startsWith("groovy/lang")) {
                isTest = false;
            }
            else if ( "junit/framework/TestCase".equals(superClassName) || "groovy/util/GroovyTestCase".equals(superClassName) ) {
                isTest = true;
            }
            else {
                final File superClassFile = getSuperTestClassFile(superClassName);
                if ( superClassFile != null ) {
                    isTest = processPossibleTestClass(superClassFile);
                }
                else
                    logger.debug("test-class-scan : failed to scan parent class {}, could not find the class file", superClassName);
            }
        }

        if ( isTest && !classVisitor.isAbstract() )
            testClassNames.add(classVisitor.getClassName() + ".class");

        return isTest;
    }
}
