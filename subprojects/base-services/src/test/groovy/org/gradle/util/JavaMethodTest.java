/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.util;

import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

@RunWith(JMock.class)
public class JavaMethodTest {
    private final JUnit4Mockery context = new JUnit4Mockery();

    @Test
    public void invokesMethodOnObject() {
        JavaMethod<CharSequence, CharSequence> method = JavaMethod.create(CharSequence.class, CharSequence.class, "subSequence", int.class, int.class);
        assertThat(method.invoke("string", 0, 3), equalTo((CharSequence) "str"));
    }
    
    @Test
    public void propagatesExceptionThrownByMethod() {
        final CharSequence mock = context.mock(CharSequence.class);
        final RuntimeException failure = new RuntimeException();
        context.checking(new Expectations() {{
            one(mock).subSequence(0, 3);
            will(throwException(failure));
        }});

        JavaMethod<CharSequence, CharSequence> method = JavaMethod.create(CharSequence.class, CharSequence.class, "subSequence", int.class, int.class);
        try {
            method.invoke(mock, 0, 3);
            fail();
        } catch (RuntimeException e) {
            assertThat(e, sameInstance(failure));
        }
    }

    @Test
    public void canAccessProtectedMethod() {
        final Package[] packages = new Package[0];
        ClassLoader classLoader = new ClassLoader() {
            @Override
            protected Package[] getPackages() {
                return packages;
            }
        };

        JavaMethod<ClassLoader, Package[]> method = JavaMethod.create(ClassLoader.class, Package[].class, "getPackages");
        assertThat(method.invoke(classLoader), sameInstance(packages));
    }
}
