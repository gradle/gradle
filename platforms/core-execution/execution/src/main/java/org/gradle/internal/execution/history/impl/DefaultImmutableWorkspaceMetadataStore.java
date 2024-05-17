/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.execution.history.impl;

import com.google.common.collect.ImmutableListMultimap;
import org.gradle.caching.internal.origin.OriginMetadata;
import org.gradle.internal.execution.history.ImmutableWorkspaceMetadata;
import org.gradle.internal.execution.history.ImmutableWorkspaceMetadataStore;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.serialize.HashCodeSerializer;
import org.gradle.internal.serialize.kryo.KryoBackedDecoder;
import org.gradle.internal.serialize.kryo.KryoBackedEncoder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;

public class DefaultImmutableWorkspaceMetadataStore implements ImmutableWorkspaceMetadataStore {
    private static final String METADATA_FILE = "metadata.bin";
    private final HashCodeSerializer hashCodeSerializer = new HashCodeSerializer();

    @Override
    public ImmutableWorkspaceMetadata loadWorkspaceMetadata(File workspace) {
        File metadataFile = new File(workspace, METADATA_FILE);
        //noinspection IOStreamConstructor
        try (KryoBackedDecoder decoder = new KryoBackedDecoder(new FileInputStream(metadataFile))) {
            OriginMetadata originMetadata = new OriginMetadata(
                decoder.readString(),
                Duration.ofMillis(decoder.readSmallLong())
            );

            int outputCount = decoder.readSmallInt();
            ImmutableListMultimap.Builder<String, HashCode> outputPropertyHashes = ImmutableListMultimap.builder();
            for (int outputIndex = 0; outputIndex < outputCount; outputIndex++) {
                String outputProperty = decoder.readString();
                int hashCount = decoder.readSmallInt();
                for (int hashIndex = 0; hashIndex < hashCount; hashIndex++) {
                    HashCode hashCode = hashCodeSerializer.read(decoder);
                    outputPropertyHashes.put(outputProperty, hashCode);
                }
            }
            return new ImmutableWorkspaceMetadata(originMetadata, outputPropertyHashes.build());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read workspace metadata from " + metadataFile, e);
        }
    }

    @Override
    public void storeWorkspaceMetadata(File workspace, ImmutableWorkspaceMetadata metadata) {
        File metadataFile = new File(workspace, METADATA_FILE);
        //noinspection IOStreamConstructor
        try (KryoBackedEncoder encoder = new KryoBackedEncoder(new FileOutputStream(metadataFile))) {
            OriginMetadata originMetadata = metadata.getOriginMetadata();
            encoder.writeString(originMetadata.getBuildInvocationId());
            encoder.writeSmallLong(originMetadata.getExecutionTime().toMillis());

            ImmutableListMultimap<String, HashCode> outputPropertyHashes = metadata.getOutputPropertyHashes();
            encoder.writeSmallInt(outputPropertyHashes.keySet().size());
            for (Map.Entry<String, Collection<HashCode>> entry : outputPropertyHashes.asMap().entrySet()) {
                encoder.writeString(entry.getKey());
                Collection<HashCode> hashes = entry.getValue();
                encoder.writeSmallInt(hashes.size());
                for (HashCode hash : hashes) {
                    hashCodeSerializer.write(encoder, hash);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write workspace metadata to " + metadataFile, e);
        }
    }
}
