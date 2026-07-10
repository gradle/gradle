/*
 * Copyright 2012 the original author or authors.
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

import org.testng.annotations.*;
import static org.testng.Assert.*;

public class ${testClassName} {
    private final ${productionClassName} production = new ${productionClassName}("value");

    @Test
    public void testOne() {
        for (int i = 0; i < 1000; i++) {
            System.out.println("Some test output from ${testClassName}.testOne - " + i);
            System.err.println("Some test error  from ${testClassName}.testOne - " + i);
        }
        assertEquals(production.getProperty(), "value");
    }

    @Test
    public void testTwo() {
        for (int i = 0; i < 1000; i++) {
            System.out.println("Some test output from ${testClassName}.testTwo - " + i);
            System.err.println("Some test error  from ${testClassName}.testTwo - " + i);
        }
        assertEquals(production.getProperty(), "value");
    }
}
