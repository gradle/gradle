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

package org.gradle.internal.concurrent;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.gradle.concurrent.ParallelismConfiguration;

import java.io.Serializable;

public class DefaultParallelismConfiguration implements Serializable, ParallelismConfiguration {
    public static final ParallelismConfiguration DEFAULT = new DefaultParallelismConfiguration();

    private boolean parallelProjectExecution;
    private int maxWorkerCount;

    public DefaultParallelismConfiguration() {
        maxWorkerCount = Runtime.getRuntime().availableProcessors();
    }

    public DefaultParallelismConfiguration(boolean parallelProjectExecution, int maxWorkerCount) {
        this.parallelProjectExecution = parallelProjectExecution;
        this.maxWorkerCount = maxWorkerCount;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isParallelProjectExecutionEnabled() {
        return parallelProjectExecution;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setParallelProjectExecutionEnabled(boolean parallelProjectExecution) {
        this.parallelProjectExecution = parallelProjectExecution;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getMaxWorkerCount() {
        return maxWorkerCount;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setMaxWorkerCount(int maxWorkerCount) {
        if (maxWorkerCount < 1) {
            throw new IllegalArgumentException("Max worker count must be > 0");
        } else {
            this.maxWorkerCount = maxWorkerCount;
        }
    }

    public boolean equals(Object obj) {
        return EqualsBuilder.reflectionEquals(this, obj);
    }

    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }
}
