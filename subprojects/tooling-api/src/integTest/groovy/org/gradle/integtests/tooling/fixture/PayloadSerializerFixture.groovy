/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.integtests.tooling.fixture

import groovy.transform.CompileStatic
import org.gradle.cache.CacheRepository
import org.gradle.cache.internal.*
import org.gradle.cache.internal.locklistener.NoOpFileLockContentionHandler
import org.gradle.internal.classloader.DefaultClassLoaderFactory
import org.gradle.internal.nativeintegration.ProcessEnvironment
import org.gradle.messaging.remote.internal.Message
import org.gradle.testfixtures.internal.NativeServicesTestFixture
import org.gradle.tooling.internal.provider.*
import org.gradle.util.GradleVersion

@CompileStatic
class PayloadSerializerFixture {
    private PayloadSerializer clientSidePayloadSerializer
    private PayloadSerializer daemonSidePayloadSerializer

    PayloadSerializerFixture(File testDirectory) {
        clientSidePayloadSerializer = forClientSide(new ClassLoaderCache())
        daemonSidePayloadSerializer = forDaemonSide(testDirectory, new ClassLoaderCache())
    }

    public <T> T makeSerializationRoundtrip(T object) {
        doPayloadSerializationRoundtrip(daemonSidePayloadSerializer, clientSidePayloadSerializer, object)
    }

    /**
     * Mimic TAPI client size PayloadSerializer
     *
     * @see org.gradle.tooling.internal.provider.ConnectionScopeServices
     */
    private static PayloadSerializer forClientSide(ClassLoaderCache classLoaderCache) {
        new PayloadSerializer(
            new ClientSidePayloadClassLoaderRegistry(
                new DefaultPayloadClassLoaderRegistry(
                    classLoaderCache,
                    new ClientSidePayloadClassLoaderFactory(
                        new ModelClassLoaderFactory(
                            new DefaultClassLoaderFactory()))),
                new ClasspathInferer()))
    }

    /**
     * Mimic Daemon size PayloadSerializer
     *
     * @see org.gradle.tooling.internal.provider.LauncherServices.ToolingBuildScopeServices
     */
    private static PayloadSerializer forDaemonSide(File testDirectory, ClassLoaderCache classLoaderCache) {
        ProcessMetaDataProvider metaDataProvider = new DefaultProcessMetaDataProvider(NativeServicesTestFixture.getInstance().get(ProcessEnvironment.class))
        CacheFactory factory = new DefaultCacheFactory(new DefaultFileLockManager(metaDataProvider, new NoOpFileLockContentionHandler()))
        CacheScopeMapping scopeMapping = new DefaultCacheScopeMapping(testDirectory, null, GradleVersion.current())
        CacheRepository cacheRepository = new DefaultCacheRepository(scopeMapping, factory);

        PayloadClassLoaderFactory classLoaderFactory = new DaemonSidePayloadClassLoaderFactory(
            new ModelClassLoaderFactory(
                new DefaultClassLoaderFactory()),
            new JarCache(),
            cacheRepository)

        new PayloadSerializer(
            new DefaultPayloadClassLoaderRegistry(
                classLoaderCache,
                classLoaderFactory)
        )
    }

    private static <T> T doPayloadSerializationRoundtrip(PayloadSerializer senderPayloadSerializer, PayloadSerializer receiverPayloadSerializer, T model) {
        SerializedPayload serializedPayload = senderPayloadSerializer.serialize(model)
        SerializedPayload deserializedPayload = doPlainJavaSerializationRoundtrip(serializedPayload)
        (T) receiverPayloadSerializer.deserialize(deserializedPayload)
    }

    private static <T> T doPlainJavaSerializationRoundtrip(T object) {
        ByteArrayOutputStream serialized = new ByteArrayOutputStream()
        Message.send(object, serialized)
        (T) Message.receive(new ByteArrayInputStream(serialized.toByteArray()), PayloadSerializerFixture.class.classLoader)
    }
}
