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

package ${packageName};

import static org.junit.Assert.*;

public class ${testClassName} {

    private final ${productionClassName} production = new ${productionClassName}("value");

    @org.junit.Test
    public void testOne() throws Exception {
        if(Boolean.getBoolean("slowTasks")) {
            Thread.sleep(10);
        }
        for (int i = 0; i < 500; i++) {
            System.out.println("Some test output from ${testClassName}.testOne - " + i);
            System.err.println("Some test error  from ${testClassName}.testOne - " + i);
        }
        assertEquals(production.getProperty(), "value");
    }

    <% if(packageName.contains('1_') && binding.hasVariable("failedTests"))   {  %>

    @org.junit.Test
    public void testFailure() throws Exception {
        for (int i = 0; i < 500; i++) {
            System.out.println("Some test output from ${testClassName}.testFailure - " + i);
            System.err.println("Some test error  from ${testClassName}.testFailure - " + i);
        }
        assertEquals(production.getProperty(), "foo");
    }
    <% } %>

    <% (binding.hasVariable("testMethodCount") ? testMethodCount : 20).times { index ->  %>
    @org.junit.Test
    public void test${index}() {
        assertEquals(production.getProperty(), "value");
        }
    <% } %>
}
