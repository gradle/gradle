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
package org.gradle.messaging.dispatch;

import org.junit.Test;

import static org.gradle.util.Matchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

public class MethodInvocationTest {
    @Test
    public void equalsAndHashCode() throws Exception {
        MethodInvocation invocation = new MethodInvocation(String.class.getMethod("length"), new Object[]{"param"});
        MethodInvocation equalInvocation = new MethodInvocation(String.class.getMethod("length"), new Object[]{"param"});
        MethodInvocation differentMethod = new MethodInvocation(String.class.getMethod("getBytes"), new Object[]{"param"});
        MethodInvocation differentArgs = new MethodInvocation(String.class.getMethod("length"), new Object[]{"a", "b"});
        assertThat(invocation, strictlyEqual(equalInvocation));
        assertThat(invocation, not(equalTo(differentMethod)));
        assertThat(invocation, not(equalTo(differentArgs)));
    }
}
