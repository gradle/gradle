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

package org.gradle.initialization;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * By Szczepan Faber on 7/5/13
 */
public class ProgressFormatter {
    private AtomicInteger remainingItems;
    private int totalItems;
    private String shortDescription;

    public ProgressFormatter(String shortDescription, int totalItems) {
        this.totalItems = totalItems;
        remainingItems = new AtomicInteger(0);
        this.shortDescription = shortDescription;
    }

    public String progress() {
        String progress = getProgress(remainingItems.incrementAndGet());
        return shortDescription + " " + progress;
    }

    private String getProgress(int current) {
        if (current > totalItems) {
            throw new IllegalStateException("All operations have already completed.");
        }
        return (int) (current * 100.0 / totalItems) + "%";
    }
}
