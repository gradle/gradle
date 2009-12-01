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

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.jmock.Expectations;

/**
 * @author Tom Eyckmans
 */
public class ReforkReasonRegisterTest {

    private JUnit4Mockery context = new JUnit4Mockery();

    @Before
    public void setUp() throws Exception
    {
        context.setImposteriser(ClassImposteriser.INSTANCE);
    }

    @Test ( expected  = IllegalArgumentException.class)
    public void registerNullReforkReason()
    {
        ReforkReasonRegister.addReforkReason(null);

        fail();
    }

    @Test ( expected  = IllegalArgumentException.class)
    public void getNullKeyReforkReason()
    {
        ReforkReasonRegister.getReforkReason(null);

        fail();
    }

    @Test
    public void registerOkReforkReason()
    {
        final ReforkReason reforkReason = context.mock(ReforkReason.class);

        context.checking(new Expectations(){{
            one(reforkReason).getKey();will(returnValue(TestReforkReasons.TEST_KEY_1));
        }});

        ReforkReasonRegister.addReforkReason(reforkReason);

        final ReforkReason retrievedReforkReason = ReforkReasonRegister.getReforkReason(TestReforkReasons.TEST_KEY_1);

        assertEquals(reforkReason, retrievedReforkReason);
    }

}
