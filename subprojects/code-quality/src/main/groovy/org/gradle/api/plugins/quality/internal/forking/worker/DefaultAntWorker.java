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

package org.gradle.api.plugins.quality.internal.forking.worker;

import java.net.URLClassLoader;
import java.util.LinkedHashMap;
import java.util.Map;
import org.codehaus.groovy.runtime.MethodClosure;
import org.gradle.api.AntBuilder;
import org.gradle.api.internal.project.AntBuilderDelegate;
import org.gradle.api.internal.project.ant.AntLoggingAdapter;
import org.gradle.api.internal.project.ant.BasicAntBuilder;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.gradle.api.plugins.quality.internal.forking.AntResult;
import org.gradle.api.plugins.quality.internal.forking.AntWorkerSpec;
import org.gradle.api.plugins.quality.internal.forking.DefaultAntResult;
import org.gradle.internal.classloader.FilteringClassLoader;
import org.gradle.internal.classloader.MultiParentClassLoader;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.util.ConfigureUtil;


public class DefaultAntWorker {

    public AntResult executeSpec(AntWorkerSpec spec) {
        Map<String, Object> antProperties = new LinkedHashMap<String, Object>();
        ClassLoader originalLoader = Thread.currentThread().getContextClassLoader();
        MultiParentClassLoader loader = createClassLoader(originalLoader, spec);
        Thread.currentThread().setContextClassLoader(loader);
        try {
            AntBuilder antBuilder = createAntBuilder(loader);

            AntBuilderDelegate delegate = new AntBuilderDelegate(antBuilder, loader);
            AntConfigurer antConfigurer = new AntConfigurer(spec);
            ConfigureUtil.configure(new MethodClosure(antConfigurer, "configureAntBuilder"), delegate);

            antProperties.putAll(antBuilder.getProject().getProperties());

            return new DefaultAntResult(antProperties);
        } catch (Exception e) {
            return new DefaultAntResult(1, e, null);
        } finally {
            Thread.currentThread().setContextClassLoader(originalLoader);
        }
    }

    private MultiParentClassLoader createClassLoader(ClassLoader originalLoader, AntWorkerSpec spec) {
        FilteringClassLoader loggingLoader = new FilteringClassLoader(getClass().getClassLoader());
        loggingLoader.allowPackage("org.slf4j");
        loggingLoader.allowPackage("org.apache.commons.logging");
        loggingLoader.allowPackage("org.apache.log4j");
        loggingLoader.allowClass(Logger.class);
        loggingLoader.allowClass(LogLevel.class);
        URLClassLoader antLoader = new URLClassLoader(new DefaultClassPath(spec.getClasspath()).getAsURLArray(), originalLoader);
        return new MultiParentClassLoader(antLoader, loggingLoader);
    }

    private static AntBuilder createAntBuilder(ClassLoader classLoader)
        throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        BasicAntBuilder antBuilder = (BasicAntBuilder) classLoader.loadClass(BasicAntBuilder.class.getName()).newInstance();
        AntLoggingAdapter antLogger = (AntLoggingAdapter) classLoader.loadClass(AntLoggingAdapter.class.getName()).newInstance();

        antBuilder.getProject().removeBuildListener(antBuilder.getProject().getBuildListeners().get(0));
        antBuilder.getProject().addBuildListener(antLogger);
        return antBuilder;
    }

    private static class AntConfigurer {
        final AntWorkerSpec spec;

        private AntConfigurer(AntWorkerSpec spec) {
            this.spec = spec;
        }

        //Used in doExecute()
        @SuppressWarnings("unused")
        public void configureAntBuilder(AntBuilderDelegate builder) {
            spec.getAction().execute(builder);
        }
    }
}
