/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.initialization.transform.utils;

import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.initialization.transform.InstrumentationArtifactMetadata;
import org.gradle.api.internal.initialization.transform.InstrumentationDependencyAnalysis;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.HierarchicalNameSerializer;
import org.gradle.internal.serialize.MapSerializer;
import org.gradle.internal.serialize.SetSerializer;
import org.gradle.internal.serialize.kryo.KryoBackedDecoder;
import org.gradle.internal.serialize.kryo.KryoBackedEncoder;

import java.io.EOFException;
import java.io.File;
import java.nio.file.Files;
import java.util.Map;
import java.util.Set;

public class DefaultInstrumentationAnalysisSerializer implements InstrumentationAnalysisSerializer {

    private final StringInterner stringInterner;

    public DefaultInstrumentationAnalysisSerializer(StringInterner stringInterner) {
        this.stringInterner = stringInterner;
    }

    @Override
    public void writeDependencyAnalysis(File output, InstrumentationDependencyAnalysis dependencyAnalysis) {
        try (KryoBackedEncoder encoder = new KryoBackedEncoder(Files.newOutputStream(output.toPath()))) {
            writeMetadata(dependencyAnalysis.getMetadata(), encoder);
            HierarchicalNameSerializer nameSerializer = new HierarchicalNameSerializer(stringInterner);
            writeTypesMap(dependencyAnalysis.getDependencies(), encoder, nameSerializer);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialize types map to a file: " + output, e);
        }
    }

    @Override
    public void writeTypeHierarchyAnalysis(File output, Map<String, Set<String>> types) {
        try (KryoBackedEncoder encoder = new KryoBackedEncoder(Files.newOutputStream(output.toPath()))) {
            HierarchicalNameSerializer nameSerializer = new HierarchicalNameSerializer(stringInterner);
            writeTypesMap(types, encoder, nameSerializer);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialize types map to a file: " + output, e);
        }
    }

    private static void writeMetadata(InstrumentationArtifactMetadata metadata, KryoBackedEncoder encoder) {
        encoder.writeString(metadata.getArtifactName());
        encoder.writeString(metadata.getArtifactHash());
    }

    private static void writeTypesMap(Map<String, Set<String>> typesMap, Encoder encoder, HierarchicalNameSerializer nameSerializer) throws Exception {
        MapSerializer<String, Set<String>> serializer = new MapSerializer<>(nameSerializer, new SetSerializer<>(nameSerializer));
        serializer.write(encoder, typesMap);
    }

    @Override
    public InstrumentationDependencyAnalysis readDependencyAnalysis(File output) {
        try (KryoBackedDecoder decoder = new KryoBackedDecoder(Files.newInputStream(output.toPath()))) {
            HierarchicalNameSerializer nameSerializer = new HierarchicalNameSerializer(stringInterner);
            return new InstrumentationDependencyAnalysis(
                readMetadata(decoder),
                readTypesMap(decoder, nameSerializer)
            );
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialize types map to a file: " + output, e);
        }
    }

    @Override
    public InstrumentationArtifactMetadata readMetadataOnly(File input) {
        try (KryoBackedDecoder decoder = new KryoBackedDecoder(Files.newInputStream(input.toPath()))) {
            return readMetadata(decoder);
        } catch (Exception e) {
            throw new IllegalStateException("Could not deserialize analysis from a file: " + input, e);
        }
    }

    @Override
    public Map<String, Set<String>> readTypeHierarchyAnalysis(File input) {
        try (KryoBackedDecoder decoder = new KryoBackedDecoder(Files.newInputStream(input.toPath()))) {
            HierarchicalNameSerializer nameSerializer = new HierarchicalNameSerializer(stringInterner);
            return readTypesMap(decoder, nameSerializer);
        } catch (Exception e) {
            throw new IllegalStateException("Could not deserialize analysis from a file: " + input, e);
        }
    }

    private static InstrumentationArtifactMetadata readMetadata(KryoBackedDecoder decoder) throws EOFException {
        return new InstrumentationArtifactMetadata(decoder.readString(), decoder.readString());
    }

    private static Map<String, Set<String>> readTypesMap(Decoder decoder, HierarchicalNameSerializer nameSerializer) throws Exception {
        MapSerializer<String, Set<String>> serializer = new MapSerializer<>(nameSerializer, new SetSerializer<>(nameSerializer));
        return serializer.read(decoder);
    }
}
