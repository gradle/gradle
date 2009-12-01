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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.*;

/**
 * @author Tom Eyckmans
 */
public class DefaultReforkContextData implements ReforkContextData {

    private Pipeline pipeline;
    private int forkId;

    private List<ReforkReasonKey> reasonKeys;
    private Map<ReforkReasonKey, Object> reasonData;

    public DefaultReforkContextData() {
        reasonKeys = new ArrayList<ReforkReasonKey>();
        reasonData = new HashMap<ReforkReasonKey, Object>();
    }

    public List<ReforkReasonKey> getReasonKeys() {
        return Collections.unmodifiableList(reasonKeys);
    }

    public Map<ReforkReasonKey, Object> getReasonData() {
        return Collections.unmodifiableMap(reasonData);
    }

    public void addReasonData(ReforkReasonKey reasonKey, Object reasonData) {
        if ( reasonKey == null ) { throw new IllegalArgumentException("reasonKey can't be null!"); }
        
        if (!reasonKeys.contains(reasonKey)) {
            reasonKeys.add(reasonKey);
        }
        if (reasonData != null) {
            this.reasonData.put(reasonKey, reasonData);
        }
    }

    public Object getReasonData(ReforkReasonKey reasonKey) {
        if ( reasonKey == null ) { throw new IllegalArgumentException("reasonKey can't be null!"); }

        return reasonData.get(reasonKey);
    }

    public boolean isEmpty() {
        return reasonKeys.isEmpty();
    }

    public Pipeline getPipeline() {
        return pipeline;
    }

    public void setPipeline(Pipeline pipeline) {
        this.pipeline = pipeline;
    }

    public int getForkId() {
        return forkId;
    }

    public void setForkId(int forkId) {
        this.forkId = forkId;
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        // pipeline not serialized (populated server side only)
        // forkId not serialized (already in message - populated on server side only)
        out.writeObject(reasonKeys);
        out.writeObject(reasonData);
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        // pipeline not serialized (populated server side only)
        // forkId not serialized (already in message - populated on server side only)
        reasonKeys = (List<ReforkReasonKey>) in.readObject();
        reasonData = (Map<ReforkReasonKey, Object>) in.readObject();
        forkId = -1;
    }
}
