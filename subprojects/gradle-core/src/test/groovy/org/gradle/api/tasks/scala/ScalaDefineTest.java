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
package org.gradle.api.tasks.scala;

import org.gradle.api.internal.AbstractTask;
import org.gradle.api.tasks.AbstractTaskTest;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Before;
import org.junit.Test;

public class ScalaDefineTest extends AbstractTaskTest {
    private ScalaDefine scalaDefine;
    private AntScalaDefine antScalaDefineMock;
    private JUnit4Mockery context = new JUnit4Mockery();

    @Override
    public AbstractTask getTask() {
        return scalaDefine;
    }

    @Override
    @Before
    public void setUp() {
        super.setUp();
        context.setImposteriser(ClassImposteriser.INSTANCE);
        scalaDefine = new ScalaDefine(getProject(), AbstractTaskTest.TEST_TASK_NAME);
        antScalaDefineMock = context.mock(AntScalaDefine.class);
        scalaDefine.setAntScalaDefine(antScalaDefineMock);
    }

    @Test
    public void testExecutesAntScalaDefine() {
        context.checking(new Expectations() {{
            one(antScalaDefineMock).execute(scalaDefine.getClasspath());
        }});
        scalaDefine.execute();
    }
}
