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

import org.gradle.api.testing.execution.Pipeline;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.util.Iterator;

/**
 * @author Tom Eyckmans
 */
public class DefaultReforkControlChecker implements ReforkControlChecker {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultReforkControlChecker.class);

    public boolean checkReforkNeeded(ReforkControl reforkControl, ReforkContextData reforkContextData) {
        if ( reforkControl == null ) { throw new IllegalArgumentException("reforkControl can't be null!"); }
        if ( reforkContextData == null ) { throw new IllegalArgumentException("reforkNeededContext can't be null!"); }

        boolean reforkNeeded = false;

        final Pipeline pipeline = reforkContextData.getPipeline();
        final int forkId = reforkContextData.getForkId();
        final Iterator<ReforkReasonKey> reforkReasonKeyIterator = reforkControl.getReforkReasonKeys().iterator();

        while (!reforkNeeded && reforkReasonKeyIterator.hasNext()) {
            final ReforkReasonKey key = reforkReasonKeyIterator.next();
            final Object data = reforkContextData.getReasonData(key);

            if (data != null) {
                final ReforkReasonDataProcessor dataProcessor = reforkControl.getDataProcessor(key);

                try {
                    reforkNeeded = dataProcessor.determineReforkNeeded(pipeline, forkId, data);
                }
                catch (Throwable t) {
                    LOGGER.error("while determening refork needed for refork reason " + key, t);
                }
            }
        }

        return reforkNeeded;
    }
}
