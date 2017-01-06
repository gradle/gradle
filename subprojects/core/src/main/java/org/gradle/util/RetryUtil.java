/*
 * Copyright 2017 the original author or authors.
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

import com.google.common.base.Function;
import com.google.common.base.Throwables;
import groovy.lang.Closure;

public final class RetryUtil {
    private RetryUtil() {}

    public static int retry(int retries, Function<Void, Void> function) {
        int retryCount = 0;
        Throwable lastException = null;

        while (retryCount++ < retries) {
            try {
                function.apply(null);
                return retryCount;
            } catch (Throwable e) {
                lastException = e;
            }
        }

        // Retry count exceeded, throwing last exception
        Throwables.propagate(lastException);
        return retryCount;
    }

    public static int retry(int retries, final Closure closure) {
        return retry(retries, new Function<Void, Void>() {
            @Override
            public Void apply(Void input) {
                closure.call();
                return null;
            }
        });
    }
}
