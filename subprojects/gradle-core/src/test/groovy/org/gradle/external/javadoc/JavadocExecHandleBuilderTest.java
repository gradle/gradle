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

package org.gradle.external.javadoc;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertTrue;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.jmock.Expectations;

import java.io.File;

/**
 * @author Tom Eyckmans
 */
public class JavadocExecHandleBuilderTest {

    private final JUnit4Mockery context = new JUnit4Mockery();

    private JavadocExecHandleBuilder javadocExecHandleBuilder;

    @Before
    public void setUp() {
        context.setImposteriser(ClassImposteriser.INSTANCE);

        javadocExecHandleBuilder = new JavadocExecHandleBuilder();
    }

    @Test ( expected = IllegalArgumentException.class )
    public void testSetNullExecDirectory() {
        javadocExecHandleBuilder.execDirectory(null);
    }

    @Test ( expected = IllegalArgumentException.class )
    public void testSetNotExistingDirectory() {
        javadocExecHandleBuilder.execDirectory(new File(".notExistingTestDirectoryX"));
    }

    @Test
    public void testSetExistingExecDirectory() {
        File existingDirectory = new File(".existingDirectory");
        assertTrue(existingDirectory.mkdir());
        try {
            javadocExecHandleBuilder.execDirectory(existingDirectory);
        }
        finally {
            assertTrue(existingDirectory.delete());
        }
    }

    @Test ( expected = IllegalArgumentException.class )
    public void testSetNullOptions() {
        javadocExecHandleBuilder.options(null);
    }

    @Test
    public void testSetNotNullOptions() {
        MinimalJavadocOptions options = context.mock(MinimalJavadocOptions.class);

        context.checking(new Expectations() {{}});

        javadocExecHandleBuilder.options(options);
    }

    @Test ( expected = IllegalArgumentException.class )
    public void testSetNullDestinationDirectory() {
        javadocExecHandleBuilder.destinationDirectory(null);
    }

    @Test ( expected = IllegalArgumentException.class )
    public void testSetNotDestionationDirectory() {
        javadocExecHandleBuilder.destinationDirectory(new File(".notExistingTestDirectoryX"));
    }

    @Test
    public void testSetExistingDestinationDirectory() {
        File existingDirectory = new File(".existingDirectory");
        assertTrue(existingDirectory.mkdir());
        try {
            javadocExecHandleBuilder.destinationDirectory(existingDirectory);
        }
        finally {
            assertTrue(existingDirectory.delete());
        }
    }
}
