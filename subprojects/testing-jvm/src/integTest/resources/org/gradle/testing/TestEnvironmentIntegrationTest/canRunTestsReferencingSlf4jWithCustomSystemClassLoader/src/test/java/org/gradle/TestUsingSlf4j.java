/*
 * Copyright 2018 the original author or authors.
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

package org.gradle;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static junit.framework.Assert.*;

public class TestUsingSlf4j {
    @Test
    public void mySystemClassLoaderIsUsed() throws ClassNotFoundException {
        assertTrue(ClassLoader.getSystemClassLoader() instanceof MySystemClassLoader);
        assertEquals(getClass().getClassLoader(), ClassLoader.getSystemClassLoader().getParent());

        Logger logger = LoggerFactory.getLogger(TestUsingSlf4j.class);
        logger.info("INFO via slf4j");
        logger.warn("WARN via slf4j");
        logger.error("ERROR via slf4j");
    }
}
