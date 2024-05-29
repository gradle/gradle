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

/**
 * This subsystem implements serialization of configuration state.
 * <h2>Key abstractions</h2>
 *
 * <h3>Encoders and Decoders</h3>
 *  <p>
 *  Serialization of an object that is supported by the configuration cache is performed by an {@link org.gradle.internal.serialize.graph.EncodingProvider}.
 *  Deserialization, on the other hand, is performed by a {@link org.gradle.internal.serialize.graph.DecodingProvider}.
 *  </p>
 *  <p>Both protocols are highly specialized and, as such, they are specified as Single Abstract Method interfaces.</p>
 *
 *  <h3>Codecs</h3>
 *  <p>A {@link org.gradle.internal.serialize.graph.Codec Codec} is an object that is both an <code>EncodingProvider</code> and a <code>DecodingProvider</code>.</p>
 *  <p>Codecs may be implemented:
 *  <ul>
 *  <li>as a custom class that implements both encoding and decoding protocols in the same class
 *  <li>based on the combination of arbitrary encoder and decoder functions, as done via {@link org.gradle.configurationcache.serialization.CombinatorsKt#codec(kotlin.jvm.functions.Function3, kotlin.jvm.functions.Function2) codec(...)}
 *  <li>or as composite/multi-type codecs, backed by a set of bindings, via {@link org.gradle.internal.serialize.graph.Bindings#build()} - see below for more on Bindings.
 *  </ul>
 *
 * <h3>Building Composite Codecs using Bindings</h3>
 * <p>In order to build composite codecs that can handle multiple types of objects, you use {@link org.gradle.internal.serialize.graph.Bindings bindings}.</p>
 * <p>
 *  Each single {@link org.gradle.internal.serialize.graph.Binding binding} comprises:
 *  <ul>
 *     <li>a <em>tag</em> (a unique numeric identifier that represents the type the binding supports)
 *     <li>the {@link org.gradle.internal.serialize.graph.EncodingProvider encoding provider} <em>{@link org.gradle.internal.serialize.graph.EncodingProvider producer}</em>
 *     <li>the {@link org.gradle.internal.serialize.graph.DecodingProvider decoding provider}
 *  </ul>
 *  <p>
 *  On serialization of an object of some type, the {@link org.gradle.internal.serialize.graph.BindingsBackedCodec bindings-backed composite codec}
 *  will query all bindings to find which one knows how to encode the type at hand,
 *  by invoking {@link org.gradle.internal.serialize.graph.Binding#encodingForType(java.lang.Class)} on each binding. If a binding supports the type,
 *  it will return the proper {@link org.gradle.internal.serialize.graph.EncodingProvider} (or null, otherwise).
 *  </p><p>
 *  Deserializing is simpler: the bindings-backed composite codec reads a tag from the stored state, and then picks the binding that is associated with that tag (and consequently, that binding's decoder).
 *  </p>
 *
 *  <h3>WriteContext</h3>
 *  <p>
 *      Before serialization starts, a {@link org.gradle.internal.serialize.graph.WriteContext} is created.
 *      The WriteContext keeps track (among other things) of the low level binary stream (actually, a Gradle serialization {@link org.gradle.internal.serialize.Encoder Encoder}) and the codec to be used.
 *  </p>
 *  <p>
 *      Object serialization is a graph-walking process, and as such, it has a recursive nature.
 *  </p>
 *  <p></p>
 *      In order to serialize one object, {@link  org.gradle.internal.serialize.graph.WriteContext#write(java.lang.Object, kotlin.coroutines.Continuation) WriteContext.write(object)} must be invoked.
 *  </p>
 *  <p><code>WriteContext.write(object)</code> then delegates to {@link org.gradle.internal.serialize.graph.Codec#encode(WriteContext, java.lang.Object, kotlin.coroutines.Continuation) Codec.encode(WriteContext, Object)}.</p>
 *  <p>A codec implementation will, generally:
 *  <ul>
 *      <li>encode the primitive/non-object values directly by invoking some of the various {@link org.gradle.internal.serialize.Encoder Encoder}'s <code>write*</code> variants</li>
 *      <li>encode any related/contained objects by delegating to {@link  org.gradle.internal.serialize.graph.WriteContext#write(java.lang.Object, kotlin.coroutines.Continuation) WriteContext.write(object)} passing the related object.</li>
 *  </ul>
 *  </p>
 */
package org.gradle.internal.serialize.graph;
