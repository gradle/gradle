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
package org.gradle.api.testing.execution.control.refork;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;

/**
 * @author Tom Eyckmans
 */
public class AmountOfTestCasesConfig extends ReforkReasonKeyLink implements ReforkReasonConfig {

    static final long DEFAULT_REFORK_EVERY = -1;

    private long reforkEvery = DEFAULT_REFORK_EVERY;

    public AmountOfTestCasesConfig(ReforkReasonKey reforkReasonKey) {
        super(reforkReasonKey);
    }

    public long getReforkEvery() {
        return reforkEvery;
    }

    public void setReforkEvery(long reforkEvery) {
        if (reforkEvery <= 0) {
            throw new IllegalArgumentException("reforkEvery needs to be larger than zero!");
        }

        this.reforkEvery = reforkEvery;
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.writeLong(reforkEvery);
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        reforkEvery = in.readLong();
    }
}
