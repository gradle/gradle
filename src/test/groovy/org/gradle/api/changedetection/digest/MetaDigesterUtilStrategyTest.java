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

package org.gradle.api.changedetection.digest;

import org.junit.Before;
import org.junit.Test;
import org.gradle.util.JUnit4GroovyMockery;
import org.gradle.util.HelperUtil;
import org.jmock.lib.legacy.ClassImposteriser;
import org.jmock.Expectations;
import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;

/**
 * @author Tom Eyckmans
 */
public class MetaDigesterUtilStrategyTest {

    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery();
    private final long okDirectorySize = 0;

    private MessageDigest digesterMock;

    private MetaDigesterUtilStrategy strategy;

    @Before
    public void setUp() {
        context.setImposteriser(ClassImposteriser.INSTANCE);

        digesterMock = context.mock(MessageDigest.class);

        strategy = new MetaDigesterUtilStrategy();
    }

    @Test
    public void digestFile() throws IOException {
        final File tempFile = File.createTempFile("gradle", "test");
        try {
            context.checking(new Expectations(){{
                one(digesterMock).update(tempFile.getAbsolutePath().getBytes());
                one(digesterMock).update(((Long)tempFile.lastModified()).byteValue());
                one(digesterMock).update(((Long)tempFile.length()).byteValue());
            }});

            strategy.digestFile(digesterMock, tempFile);

        }
        finally {
            assertTrue(tempFile.delete());
        }
    }

    @Test
    public void digestDirectory() throws IOException {
        final File tempDir = HelperUtil.makeNewTestDir();
        try {
            context.checking(new Expectations(){{
                one(digesterMock).update(tempDir.getAbsolutePath().getBytes());
                one(digesterMock).update(((Long)tempDir.lastModified()).byteValue());
                one(digesterMock).update(((Long)okDirectorySize).byteValue());
            }});

            strategy.digestDirectory(digesterMock, tempDir, okDirectorySize);
        }
        finally {
            assertTrue(tempDir.delete());
        }
    }

}
