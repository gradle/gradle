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

package org.gradle.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author Tom Eyckmans
 */
public class ThreadUtils {
    public static <T extends ExecutorService> void shutdown(T executorService) {
        shutdown(executorService, new IgnoreInterruptHandler<T>());
    }

    public static <T extends ExecutorService> void shutdown(T executorService, InterruptHandler<T> interruptHandler) {
        executorService.shutdown();

        awaitTermination(executorService, interruptHandler);
    }

    public static <T extends ExecutorService> void awaitTermination(T executorService) {
        awaitTermination(executorService, new IgnoreInterruptHandler<T>());
    }

    public static <T extends ExecutorService> void awaitTermination(T executorService,
                                                                    InterruptHandler<T> interruptHandler) {
        boolean stopped = false;
        while (!stopped) {
            try {
                executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
                stopped = true;
            } catch (InterruptedException e) {
                stopped = interruptHandler.handleIterrupt(executorService, e);
            }
        }
    }
}
