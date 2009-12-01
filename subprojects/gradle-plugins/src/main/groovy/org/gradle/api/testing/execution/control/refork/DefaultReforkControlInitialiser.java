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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * @author Tom Eyckmans
 */
public class DefaultReforkControlInitialiser {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultReforkControlInitialiser.class);

    private ReforkReasonRegisterAdapter reforkReasonRegisterAdapter;

    public DefaultReforkControlInitialiser() {
        reforkReasonRegisterAdapter = new DefaultReforkReasonRegisterAdapter();
    }

    public void initialize(ReforkControl reforkControl, ReforkReasonConfigs configs)
    {
        if ( reforkControl == null ) { throw new IllegalArgumentException("reforkControl can't be null!"); }

        if ( configs != null ) {
            final Map<ReforkReasonKey, ReforkReasonConfig> reasonConfigs = configs.getConfigs();

            for (final ReforkReasonKey key : configs.getKeys()) {
                final ReforkReason reforkReason = reforkReasonRegisterAdapter.getReforkReason(key);
                final ReforkReasonConfig config = reasonConfigs.get(key);
                final ReforkReasonDataProcessor dataProcessor = reforkReason.getDataProcessor();

                try {
                    dataProcessor.configure(config);

                    reforkControl.addDataProcessor(dataProcessor);
                }
                catch ( Throwable t ) {
                    LOGGER.error("failed to configure dataProcessor for refork reason " + key, t);
                }
            }
        }
        // else nothing to do
    }

    public void setReforkReasonRegisterAdapter(ReforkReasonRegisterAdapter reforkReasonRegisterAdapter) {
        if ( reforkReasonRegisterAdapter == null ) { throw new IllegalArgumentException("reforkReasonRegisterAdapter can't be null!"); }

        this.reforkReasonRegisterAdapter = reforkReasonRegisterAdapter;
    }
}
