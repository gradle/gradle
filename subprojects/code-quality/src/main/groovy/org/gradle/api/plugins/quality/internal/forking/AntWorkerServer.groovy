/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.plugins.quality.internal.forking
import org.gradle.api.Action
import org.gradle.api.AntBuilder
import org.gradle.api.internal.project.AntBuilderDelegate
import org.gradle.api.internal.project.ant.AntLoggingAdapter
import org.gradle.api.internal.project.ant.BasicAntBuilder
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger
import org.gradle.internal.classloader.FilteringClassLoader
import org.gradle.internal.classloader.MultiParentClassLoader
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.process.internal.WorkerProcessContext
import org.gradle.util.ConfigureUtil

public class AntWorkerServer implements Action<WorkerProcessContext>, Serializable {

    private AntWorkerSpec antWorkerSpec;

    public AntWorkerServer(AntWorkerSpec antWorkerSpec) {
        this.antWorkerSpec = antWorkerSpec;
    }

    @Override
    public void execute(WorkerProcessContext context) {
        AntResult result = doExecute();

        final AntWorkerClientProtocol clientProtocol = context.getServerConnection().addOutgoing(AntWorkerClientProtocol.class);
        context.getServerConnection().connect();
        clientProtocol.executed(result);
    }

    private AntResult doExecute() {
        Map<String, Object> antProperties = new LinkedHashMap<String, Object>();
        ClassLoader originalLoader = Thread.currentThread().contextClassLoader
        MultiParentClassLoader loader = createClassLoader(originalLoader)
        Thread.currentThread().contextClassLoader = loader
        try {
            AntBuilder antBuilder = createAntBuilder(loader);

            Object delegate = new AntBuilderDelegate(antBuilder, loader)
            ConfigureUtil.configure({ AntBuilderDelegate builder ->
                antWorkerSpec.getAction().configure(builder);
            }, delegate)

            antProperties.putAll(antBuilder.getProject().getProperties());

            return new DefaultAntResult(antProperties);
        } catch (Exception e) {
            return new DefaultAntResult(1, e, null);
        } finally {
            Thread.currentThread().setContextClassLoader(originalLoader);
        }
    }

    private MultiParentClassLoader createClassLoader(ClassLoader originalLoader) {
        def loggingLoader = new FilteringClassLoader(getClass().classLoader)
        loggingLoader.allowPackage('org.slf4j')
        loggingLoader.allowPackage('org.apache.commons.logging')
        loggingLoader.allowPackage('org.apache.log4j')
        loggingLoader.allowClass(Logger)
        loggingLoader.allowClass(LogLevel)
        def antLoader = new URLClassLoader(new DefaultClassPath(antWorkerSpec.classpath).asURLArray, originalLoader)
        def loader = new MultiParentClassLoader(antLoader, loggingLoader)
        return loader
    }

    static private AntBuilder createAntBuilder(ClassLoader classLoader) {
        BasicAntBuilder antBuilder = (BasicAntBuilder)classLoader.loadClass(BasicAntBuilder.class.name).newInstance()
        AntLoggingAdapter antLogger = (AntLoggingAdapter)classLoader.loadClass(AntLoggingAdapter.class.name).newInstance()

        antBuilder.project.removeBuildListener(antBuilder.project.getBuildListeners()[0])
        antBuilder.project.addBuildListener(antLogger)
        return antBuilder;
    }
}
