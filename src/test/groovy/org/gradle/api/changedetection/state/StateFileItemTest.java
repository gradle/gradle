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

package org.gradle.api.changedetection.state;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * @author Tom Eyckmans
 */
public class StateFileItemTest {

    private String okKey = "okKey";
    private String okDigest = "okDigest";

    private StateFileItem stateFileItem;

    @Test ( expected = IllegalArgumentException.class )
    public void createWithNullKey() {
        stateFileItem = new StateFileItem(null, okDigest);
    }

    @Test ( expected = IllegalArgumentException.class )
    public void createWithNullDigest() {
        stateFileItem = new StateFileItem(okKey, null);
    }

    @Test
    public void createWithEmptyKey() {
        stateFileItem = new StateFileItem("", okDigest);

        assertNotNull(stateFileItem);
        assertEquals("", stateFileItem.getKey());
        assertEquals(okDigest, stateFileItem.getDigest());
    }

    @Test
    public void create() {
        stateFileItem = new StateFileItem(okKey, okDigest);

        assertNotNull(stateFileItem);
        assertEquals(okKey, stateFileItem.getKey());
        assertEquals(okDigest, stateFileItem.getDigest());
    }
}
