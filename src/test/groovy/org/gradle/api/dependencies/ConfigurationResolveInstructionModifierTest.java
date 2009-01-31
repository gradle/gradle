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
package org.gradle.api.dependencies;

import org.junit.Test;
import static org.junit.Assert.assertThat;
import org.hamcrest.Matchers;
import static org.hamcrest.Matchers.*;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.Expectations;

/**
 * @author Hans Dockter
 */
public class ConfigurationResolveInstructionModifierTest {
    private static final String TEST_CONF = "name";

    private JUnit4Mockery context = new JUnit4Mockery();

    private ResolveInstructionModifier modifier = context.mock(ResolveInstructionModifier.class);

    @Test
    public void initWithName() {
        ConfigurationResolveInstructionModifier resolveInstructionModifier = new ConfigurationResolveInstructionModifier(TEST_CONF);
        assertThat(resolveInstructionModifier.getConfiguration(), equalTo(TEST_CONF));
        assertThat(resolveInstructionModifier.getResolveInstructionModifier(), equalTo(ResolveInstructionModifiers.DO_NOTHING_MODIFIER));
    }

    @Test
    public void initWithNameAndModifier() {
        ConfigurationResolveInstructionModifier resolveInstructionModifier = new ConfigurationResolveInstructionModifier(TEST_CONF, modifier);
        assertThat(resolveInstructionModifier.getConfiguration(), equalTo(TEST_CONF));
        assertThat(resolveInstructionModifier.getResolveInstructionModifier(), sameInstance(modifier));
    }

    @Test
    public void testModify() {
        final ConfigurationResolveInstructionModifier resolveInstructionModifier = new ConfigurationResolveInstructionModifier(TEST_CONF, modifier);
        final ResolveInstruction inputInstruction = new ResolveInstruction().setTransitive(false);
        final ResolveInstruction outputInstruction = new ResolveInstruction().setTransitive(true);
        context.checking(new Expectations() {{
            one(modifier).modify(inputInstruction);
            will(returnValue(outputInstruction));
        }});
        assertThat(resolveInstructionModifier.modify(inputInstruction), sameInstance(outputInstruction));
    }
}
