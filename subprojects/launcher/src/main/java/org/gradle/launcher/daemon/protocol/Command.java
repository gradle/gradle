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
package org.gradle.launcher.daemon.protocol;

import org.gradle.initialization.BuildClientMetaData;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicInteger;

public class Command implements Serializable {

    private static final AtomicInteger SEQUENCER = new AtomicInteger(1);

    private final BuildClientMetaData clientMetaData;
    private final String identifier;

    public Command(BuildClientMetaData clientMetaData) {
        this.clientMetaData = clientMetaData;
        //unique only within the process but this should be enough
        this.identifier = System.currentTimeMillis() + "-" + SEQUENCER.getAndIncrement();
    }

    public BuildClientMetaData getClientMetaData() {
        return clientMetaData;
    }

    /**
     * @return an id that is guaranteed to be unique in the same process
     */
    public String getIdentifier() {
        return identifier;
    }

    @Override
    public String toString() {
        return String.format("%s[id=%s]", getClass().getSimpleName(), identifier);
    }
}
