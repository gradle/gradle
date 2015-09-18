package org.gradle.api.plugins.quality.internal.forking;

import java.io.Serializable;
import java.net.URLClassLoader;
import java.util.LinkedHashMap;
import java.util.Map;
import org.codehaus.groovy.runtime.MethodClosure;
import org.gradle.api.Action;
import org.gradle.api.AntBuilder;
import org.gradle.api.internal.project.AntBuilderDelegate;
import org.gradle.api.internal.project.ant.AntLoggingAdapter;
import org.gradle.api.internal.project.ant.BasicAntBuilder;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.gradle.internal.classloader.FilteringClassLoader;
import org.gradle.internal.classloader.MultiParentClassLoader;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.process.internal.WorkerProcessContext;
import org.gradle.util.ConfigureUtil;


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
        ClassLoader originalLoader = Thread.currentThread().getContextClassLoader();
        MultiParentClassLoader loader = createClassLoader(originalLoader);
        Thread.currentThread().setContextClassLoader(loader);
        try {
            AntBuilder antBuilder = createAntBuilder(loader);

            Object delegate = new AntBuilderDelegate(antBuilder, loader);
            ConfigureUtil.configure(new MethodClosure(this, "configureAntBuilder"), (AntBuilderDelegate) delegate);

            antProperties.putAll(antBuilder.getProject().getProperties());

            return new DefaultAntResult(antProperties);
        } catch (Exception e) {
            return new DefaultAntResult(1, e, null);
        } finally {
            Thread.currentThread().setContextClassLoader(originalLoader);
        }
    }

    //Used in doExecute()
    @SuppressWarnings("unused")
    public void configureAntBuilder(AntBuilderDelegate builder) {
        antWorkerSpec.getAction().execute(builder);
    }

    private MultiParentClassLoader createClassLoader(ClassLoader originalLoader) {
        FilteringClassLoader loggingLoader = new FilteringClassLoader(getClass().getClassLoader());
        loggingLoader.allowPackage("org.slf4j");
        loggingLoader.allowPackage("org.apache.commons.logging");
        loggingLoader.allowPackage("org.apache.log4j");
        loggingLoader.allowClass(Logger.class);
        loggingLoader.allowClass(LogLevel.class);
        URLClassLoader antLoader = new URLClassLoader(new DefaultClassPath(antWorkerSpec.getClasspath()).getAsURLArray(), originalLoader);
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
}
