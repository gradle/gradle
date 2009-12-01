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
package org.gradle.api.testing.execution.control.refork;

import org.junit.Test;
import org.junit.Before;
import static org.junit.Assert.*;
import org.apache.commons.lang.SerializationUtils;

import java.io.IOException;

/**
 * @author Tom Eyckmans
 */
public class AmountOfTestCasesConfigTest {

    static final long OK_REFORK_EVERY = 20;

    private AmountOfTestCasesConfig config;

    @Before
    public void setUp() throws Exception
    {
        config = new AmountOfTestCasesConfig(ReforkReasons.AMOUNT_OF_TESTCASES);
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullReforkReasonConstructorTest()
    {
        config = new AmountOfTestCasesConfig(null);

        fail();
    }

    @Test
    public void constructorTest()
    {
        config = new AmountOfTestCasesConfig(ReforkReasons.AMOUNT_OF_TESTCASES);

        assertEquals(AmountOfTestCasesConfig.DEFAULT_REFORK_EVERY, config.getReforkEvery());
    }

    @Test(expected = IllegalArgumentException.class)
    public void setNegativeReforkEveryTest()
    {
        config.setReforkEvery(-1);

        fail();
    }

    @Test(expected = IllegalArgumentException.class)
    public void setZeroReforkEveryTest()
    {
        config.setReforkEvery(0);

        fail();
    }

    @Test
    public void setOkReforkEvery()
    {
        config.setReforkEvery(OK_REFORK_EVERY);

        assertEquals(OK_REFORK_EVERY, config.getReforkEvery());
    }

    @Test
    public void serializationTest() throws IOException, ClassNotFoundException
    {
        config.setReforkEvery(OK_REFORK_EVERY);

        final byte[] objectBytes = SerializationUtils.serialize(config);

        final AmountOfTestCasesConfig deserialized = (AmountOfTestCasesConfig)SerializationUtils.deserialize(objectBytes);

        assertEquals(OK_REFORK_EVERY, deserialized.getReforkEvery());
    }

}
