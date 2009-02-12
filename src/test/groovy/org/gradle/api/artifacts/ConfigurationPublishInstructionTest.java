/*
 * Copyright 2007-2009 the original author or authors.
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
package org.gradle.api.artifacts;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Hans Dockter
 */
public class ConfigurationPublishInstructionTest extends PublishInstructionTest {
    private static final String TEST_CONF = "conf";
    private ConfigurationPublishInstruction configurationPublishInstruction;

    @Before
    public void setUp() {
        configurationPublishInstruction = new ConfigurationPublishInstruction(TEST_CONF);
    }

    @Override
    protected PublishInstruction getInstruction() {
        return configurationPublishInstruction;
    }

    @Test
    public void init() {
        assertThat(configurationPublishInstruction.getConfiguration(), equalTo(TEST_CONF));
        super.init();
    }
}
