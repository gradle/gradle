/*
 * Copyright 2013 the original author or authors.
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

import java.io.DataInput;
import java.io.IOException;

public class GStreamUtil {

    //skips 'n' bytes,
    public static int skipBytes(int n, DataInput input) throws IOException {
        int total = 0;
        int cur;

        while ((total<n) && ((cur = input.skipBytes(n - total)) > 0)) {
            total += cur;
        }
        return total;
    }
}
