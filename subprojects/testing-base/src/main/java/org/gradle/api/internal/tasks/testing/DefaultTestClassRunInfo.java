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
package org.gradle.api.internal.tasks.testing;

import org.apache.commons.lang.StringUtils;

public class DefaultTestClassRunInfo implements TestClassRunInfo {
    private String testClassName;

    public DefaultTestClassRunInfo(String testClassName) {
        if (StringUtils.isEmpty(testClassName)) {
            throw new IllegalArgumentException("testClassName is empty!");
        }

        this.testClassName = testClassName;
    }

    @Override
    public String getTestClassName() {
        return testClassName;
    }
}
