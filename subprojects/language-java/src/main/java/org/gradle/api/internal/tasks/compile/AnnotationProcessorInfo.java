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

package org.gradle.api.internal.tasks.compile;

import com.google.common.base.Objects;

import org.gradle.incap.ProcessorType;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;

import java.util.Map;

/**
 * Encapsulates information computed for each discovered annotation processor.
 */
public class AnnotationProcessorInfo {

    static final Serializer<AnnotationProcessorInfo> SERIALIZER = new AnnotationProcessorInfoSerializer();

    private static final String UNKNOWN_NAME = "(unknown processor)";

    // Need this flag to mark processors as such, even if not incap-compliant.
    private boolean isProcessor;
    
    private ProcessorType processorType = ProcessorType.UNSPECIFIED;
    
    private String processorName;

    public AnnotationProcessorInfo() {
    }

    /**
     * Sets the incap support level.
     */
    public void setIncapSupportType(ProcessorType type) {
        processorType = type;
    }

    public ProcessorType getIncapSupportType() {
        return processorType;
    }

    /**
     * Returns {@code true} if the annotation processor adheres to the INCAP incremental AP spec.
     */
    public boolean isIncrementalEnabled() {
        return isProcessor && (processorType == ProcessorType.SIMPLE
                               || processorType == ProcessorType.AGGREGATING);
    }
    
    /**
     * Sets the user-visible name of the processor.
     */
    public void setName(String name) {
        processorName = name;
    }

    /**
     * Returns a user-presentable name for the processor.
     */
    public String getName() {
        return processorName != null ? processorName : UNKNOWN_NAME;
    }

    /**
     * Flags this cache entry as an annotation processor.
     */
    public void setProcessor(boolean processor) {
        isProcessor = processor;
    }

    /**
     * Returns true if processor services were found in this file.
     */
    public boolean isProcessor() {
        return isProcessor;
    }

    /**
     * Returns true if this cache entry's name has been set.
     */
    public boolean isNamed() {
        return processorName != null;
    }

    private static class AnnotationProcessorInfoSerializer extends AbstractSerializer<AnnotationProcessorInfo> {
        public AnnotationProcessorInfo read(Decoder decoder) throws Exception {
            AnnotationProcessorInfo result = new AnnotationProcessorInfo();
            result.isProcessor = decoder.readBoolean();
            result.processorName = decoder.readString();
            result.processorType = ProcessorType.valueOf(decoder.readString());
            return result;
        }

        public void write(Encoder encoder, AnnotationProcessorInfo value) throws Exception {
            encoder.writeBoolean(value.isProcessor);
            encoder.writeString(value.getName());
            encoder.writeString(value.getIncapSupportType().toString());
        }
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(isProcessor, getName(), getIncapSupportType());
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof AnnotationProcessorInfo)) {
            return false;
        }
        AnnotationProcessorInfo other = (AnnotationProcessorInfo)obj;
        return other.isProcessor == this.isProcessor
                && Objects.equal(other.processorName, this.processorName)
                && Objects.equal(other.processorType, this.processorType);
    }

    @Override
    public String toString() {
        return "AnnotationProcessorInfo{processor=" + isProcessor() +
            ", name=" + getName() +
            ", type=" + getIncapSupportType() +
            ", incremental=" + isIncrementalEnabled() + "}";
    }
}
