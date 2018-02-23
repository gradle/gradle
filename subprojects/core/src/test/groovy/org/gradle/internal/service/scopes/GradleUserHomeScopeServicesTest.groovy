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

package org.gradle.internal.service.scopes

import org.gradle.StartParameter
import org.gradle.api.internal.ClassPathRegistry
import org.gradle.api.internal.cache.StringInterner
import org.gradle.api.internal.changedetection.state.CrossBuildFileHashCache
import org.gradle.api.internal.changedetection.state.FileSystemMirror
import org.gradle.api.internal.changedetection.state.FileSystemSnapshotter
import org.gradle.api.internal.changedetection.state.GlobalScopeFileTimeStampInspector
import org.gradle.api.internal.changedetection.state.InMemoryCacheDecoratorFactory
import org.gradle.api.internal.changedetection.state.ValueSnapshotter
import org.gradle.api.internal.classpath.ModuleRegistry
import org.gradle.api.internal.file.TemporaryFileProvider
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory
import org.gradle.api.internal.initialization.loadercache.ClassLoaderCache
import org.gradle.cache.CacheDecorator
import org.gradle.cache.PersistentCache
import org.gradle.cache.internal.CacheFactory
import org.gradle.cache.internal.CrossBuildInMemoryCacheFactory
import org.gradle.concurrent.ParallelismConfiguration
import org.gradle.groovy.scripts.internal.CrossBuildInMemoryCachingScriptClassCache
import org.gradle.initialization.ClassLoaderRegistry
import org.gradle.initialization.GradleUserHomeDirProvider
import org.gradle.internal.classloader.ClassLoaderHierarchyHasher
import org.gradle.internal.classloader.HashingClassLoaderFactory
import org.gradle.internal.classpath.CachedClasspathTransformer
import org.gradle.internal.concurrent.ExecutorFactory
import org.gradle.internal.concurrent.ParallelismConfigurationManager
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.hash.ContentHasherFactory
import org.gradle.internal.hash.FileHasher
import org.gradle.internal.hash.StreamHasher
import org.gradle.internal.jvm.inspection.JvmVersionDetector
import org.gradle.internal.logging.LoggingManagerInternal
import org.gradle.internal.logging.events.OutputEventListener
import org.gradle.internal.logging.progress.ProgressLoggerFactory
import org.gradle.internal.nativeintegration.filesystem.FileSystem
import org.gradle.internal.remote.MessagingServer
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.service.ServiceRegistryBuilder
import org.gradle.internal.time.Clock
import org.gradle.process.internal.JavaExecHandleFactory
import org.gradle.process.internal.health.memory.MemoryManager
import org.gradle.process.internal.worker.WorkerProcessFactory
import org.gradle.process.internal.worker.child.WorkerProcessClassPathProvider
import spock.lang.Specification
import spock.lang.Unroll

class GradleUserHomeScopeServicesTest extends Specification {
    ServiceRegistry parent = Stub(ServiceRegistry)
    ServiceRegistry registry

    def setup() {
        parent.getAll(PluginServiceRegistry) >> []
        registry =  ServiceRegistryBuilder.builder()
            .parent(parent)
            .provider(new Object() {
                GradleUserHomeDirProvider createGradleUserHomeDirProvider() {
                    return new GradleUserHomeDirProvider() {
                        @Override
                        public File getGradleUserHomeDirectory() {
                            return new File("")
                        }
                    }
                }
            })
            .provider(new GradleUserHomeScopeServices(parent))
            .build()
    }

    @Unroll
    def "provides a #serviceType.simpleName"() {
        given:
        expectParentServiceLocated(ListenerManager) {
            _ * it.createChild() >> Mock(ListenerManager)
        }
        expectParentServiceLocated(ParallelismConfigurationManager) {
            _ * it.getParallelismConfiguration() >> Mock(ParallelismConfiguration)
        }
        expectParentServiceLocated(InMemoryCacheDecoratorFactory) {
            _ * it.decorator(_, _) >> Mock(CacheDecorator)
        }
        expectParentServiceLocated(CacheFactory) {
            _ * it.open(_, _, _, _, _, _, _, _) >> Mock(PersistentCache) { _ * getBaseDir() >> Mock(File) }
        }
        expectParentServiceLocated(LoggingManagerInternal)
        expectParentServiceLocated(Clock)
        expectParentServiceLocated(ProgressLoggerFactory)
        expectParentServiceLocated(StartParameter)
        expectParentServiceLocated(ExecutorFactory)
        expectParentServiceLocated(ModuleRegistry)
        expectParentServiceLocated(MessagingServer)
        expectParentServiceLocated(TemporaryFileProvider)
        expectParentServiceLocated(JavaExecHandleFactory)
        expectParentServiceLocated(JvmVersionDetector)
        expectParentServiceLocated(MemoryManager)
        expectParentServiceLocated(OutputEventListener)
        expectParentServiceLocated(StringInterner)
        expectParentServiceLocated(FileSystem)
        expectParentServiceLocated(CrossBuildInMemoryCacheFactory)
        expectParentServiceLocated(ClassLoaderRegistry)
        expectParentServiceLocated(DirectoryFileTreeFactory)
        expectParentServiceLocated(ContentHasherFactory)
        expectParentServiceLocated(StreamHasher)

        expect:
        findsAndCachesService(serviceType)

        where:
        serviceType << [
            ListenerManager,
            CrossBuildFileHashCache,
            GlobalScopeFileTimeStampInspector,
            FileHasher,
            CrossBuildInMemoryCachingScriptClassCache,
            ValueSnapshotter,
            ClassLoaderHierarchyHasher,
            FileSystemMirror,
            FileSystemSnapshotter,
            HashingClassLoaderFactory,
            ClassLoaderCache,
            CachedClasspathTransformer,
            WorkerProcessFactory,
            ClassPathRegistry,
            WorkerProcessClassPathProvider
        ]
    }

    boolean findsAndCachesService(Class<?> serviceType) {
        assert serviceType.isAssignableFrom(registry.get(serviceType).class) : "${registry.get(serviceType).class.name} is not an instance of ${serviceType.simpleName}"
        assert registry.get(serviceType) == registry.get(serviceType)
        true
    }

    private <T> T expectParentServiceLocated(Class<T> type) {
        T t = Mock(type)
        parent.get(type) >> t
        return t
    }

    private <T> T expectParentServiceLocated(Class<T> type, Closure closure) {
        T t = expectParentServiceLocated(type)
        t.with closure
    }
}
