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

/**
 * Defines a criteria that can be used to determine when a test fork should be restarted.
 *
 * @author Tom Eyckmans
 */
public interface ReforkReason {
    /**
     * Defines a unique id to identify the criteria.
     *
     * @return Key of the criteria.
     */
    ReforkReasonKey getKey();

    /**
     * Returns an instance of the data gatherer that needs to be used for this criteria.
     * <p/>
     * Operates inside the test fork.
     *
     * @return A data gatherer instance.
     */
    ReforkReasonDataGatherer getDataGatherer();

    /**
     * Returns an instance of the data processor that needs to be used for this criteria.
     * <p/>
     * Operates inside the test server.
     *
     * @return A data processor instance.
     */
    ReforkReasonDataProcessor getDataProcessor();

    /**
     * Returns an instance of the configuration class used by this criteria.
     * <p/>
     * Instanciated on the test server, used inside the test fork.
     *
     * @return A config class instance. Null in case no configuration is needed.
     */
    ReforkReasonConfig getConfig();
}
