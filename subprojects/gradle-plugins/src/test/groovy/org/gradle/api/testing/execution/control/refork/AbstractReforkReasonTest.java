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
import static org.junit.Assert.*;

/**
 * @author Tom Eyckmans
 */
public abstract class AbstractReforkReasonTest {

    protected ReforkReasonKey key;
    protected ReforkReason reforkReason;

    @Test
    public void checkKey()
    {
        assertEquals(key, reforkReason.getKey());
    }

    @Test
    public void getConfigTest()
    {
        final ReforkReasonConfig config = reforkReason.getConfig();

        assertNotNull(config);
        assertEquals(key, config.getKey());
    }

    @Test
    public void getDataGathererTest()
    {
        final ReforkReasonDataGatherer dataGatherer = reforkReason.getDataGatherer();

        assertNotNull(dataGatherer);
        assertEquals(key, dataGatherer.getKey());
    }

    @Test
    public void getDataProcessorTest()
    {
        final ReforkReasonDataProcessor dataProcessor = reforkReason.getDataProcessor();

        assertNotNull(dataProcessor);
    }
}
