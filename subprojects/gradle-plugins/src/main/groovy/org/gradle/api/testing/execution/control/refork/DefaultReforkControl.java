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

import org.gradle.api.testing.execution.PipelineConfig;
import org.gradle.api.testing.execution.Pipeline;

import java.util.*;

/**
 * @author Tom Eyckmans
 */
public class DefaultReforkControl implements ReforkControl {

    private final List<ReforkReasonKey> orderedReforkReasons;
    private final Map<ReforkReasonKey, ReforkReasonDataProcessor> reforkReasonDataProcessors;
    private DefaultReforkControlInitialiser initialiser;
    private ReforkControlChecker checker;

    public DefaultReforkControl() {
        orderedReforkReasons = new ArrayList<ReforkReasonKey>();
        reforkReasonDataProcessors = new HashMap<ReforkReasonKey, ReforkReasonDataProcessor>();

        initialiser = new DefaultReforkControlInitialiser();
        checker = new DefaultReforkControlChecker();
    }

    public void initialize(Pipeline pipeline) {
        if ( pipeline == null ) {
            throw new IllegalArgumentException("pipeline can't be null!");
        }

        final PipelineConfig pipelineConfig = pipeline.getConfig();
        final ReforkReasonConfigs reforkReasonConfigs = pipelineConfig.getReforkReasonConfigs();

        initialiser.initialize(this, reforkReasonConfigs);
    }

    public List<ReforkReasonKey> getReforkReasonKeys() {
        return Collections.unmodifiableList(orderedReforkReasons);
    }

    public boolean reforkNeeded(ReforkContextData reforkContextData) {
        if ( reforkContextData == null ) { throw new IllegalArgumentException("reforkNeededContext can't be null!"); }

        return checker.checkReforkNeeded(this, reforkContextData);
    }

    public void addDataProcessor(ReforkReasonDataProcessor dataProcessor)
    {
        if ( dataProcessor == null ) { throw new IllegalArgumentException("dataProcessor can't be null!"); }

        final ReforkReasonKey key = dataProcessor.getKey();

        orderedReforkReasons.add(key);
        reforkReasonDataProcessors.put(key, dataProcessor);
    }

    public ReforkReasonDataProcessor getDataProcessor(ReforkReasonKey key)
    {
        if ( key == null ) { throw new IllegalArgumentException("key can't be null!"); }
        
        return reforkReasonDataProcessors.get(key);
    }

    public void setInitialiser(DefaultReforkControlInitialiser initialiser) {
        if ( initialiser == null ) { throw new IllegalArgumentException("initialiser can't be null!"); }

        this.initialiser = initialiser;
    }

    public void setChecker(ReforkControlChecker checker) {
        if ( checker == null ) { throw new IllegalArgumentException("checker can't be null!"); }

        this.checker = checker;
    }
}
