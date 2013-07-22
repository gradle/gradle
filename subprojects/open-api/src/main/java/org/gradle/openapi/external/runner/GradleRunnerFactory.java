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
package org.gradle.openapi.external.runner;

import org.gradle.openapi.external.ExternalUtility;

import java.io.File;
import java.lang.reflect.Constructor;

/**
 * This provides a simple way to execute gradle commands from an external process. call createGradleRunner to instantiate a gradle runner. You can then use that to execute commands.
 * @deprecated Use the tooling API instead.
 */
@Deprecated
public class GradleRunnerFactory {
    /**
     * Call this to instantiate an object that you can use to execute gradle commands directly.
     *
     * Note: this function is meant to be backward and forward compatible. So this signature should not change at all, however, it may take and return objects that implement ADDITIONAL interfaces. That
     * is, it will always return a GradleRunnerVersion1, but it may also be an object that implements GradleRunnerVersion2 (notice the 2). The caller will need to dynamically determine that. The
     * GradleRunnerInteractionVersion1 may take an object that also implements GradleRunnerInteractionVersion2. If so, we'll dynamically determine that and handle it. Of course, this all depends on
     * what happens in the future.
     *
     * @param parentClassLoader Your classloader. Probably the classloader of whatever class is calling this.
     * @param gradleHomeDirectory the root directory of a gradle installation
     * @param interaction this is how we interact with the caller.
     * @param showDebugInfo true to show some additional information that may be helpful diagnosing problems is this fails
     * @return a gradle runner
     * @deprecated Use the tooling API instead.
     */
    @Deprecated
    public static GradleRunnerVersion1 createGradleRunner(ClassLoader parentClassLoader, File gradleHomeDirectory, GradleRunnerInteractionVersion1 interaction, boolean showDebugInfo)
            throws Exception {
        //much of this function is exception handling so if we can't obtain it via the newer factory method, then
        //we'll try the old way, but we want to report the original exception if we can't do it either way.
        Exception viaFactoryException = null;
        GradleRunnerVersion1 gradleRunner = null;

        //first, try it the new way
        try {
            gradleRunner = createGradleRunnerViaFactory(parentClassLoader, gradleHomeDirectory, interaction, showDebugInfo);
        } catch (Exception e) {
            //we might ignore this. It means we're probably using an older version of gradle. That case is handled below.
            //If not, this exception will be thrown at the end.
            viaFactoryException = e;
        }

        //try it the old way
        if (gradleRunner == null) {
            gradleRunner = createGradleRunnerOldWay(parentClassLoader, gradleHomeDirectory, interaction, showDebugInfo);
        }

        //if we still don't have a gradle runner and we have an exception from using the factory, throw it. If we
        //got an exception using the 'old way', it would have been thrown already and we wouldn't be here.
        if (gradleRunner == null && viaFactoryException != null) {
            throw viaFactoryException;
        }

        return gradleRunner;
    }

    /**
     * This function uses a factory to instantiate a GradleRunner. The factory is located with the version of gradle pointed to by gradleHomeDirectory and thus allows the version of gradle being loaded
     * to make decisions about how to instantiate the runner. This is needed as multiple versions of the runner are being used.
     */
    private static GradleRunnerVersion1 createGradleRunnerViaFactory(ClassLoader parentClassLoader, File gradleHomeDirectory, GradleRunnerInteractionVersion1 interaction, boolean showDebugInfo)
            throws Exception {
        //load the class in gradle that wraps our return interface and handles versioning issues.
        Class soughtClass = ExternalUtility.loadGradleClass("org.gradle.openapi.wrappers.RunnerWrapperFactory", parentClassLoader, gradleHomeDirectory, showDebugInfo);
        if (soughtClass == null) {
            return null;
        }

        Class[] argumentClasses = new Class[]{File.class, GradleRunnerInteractionVersion1.class, boolean.class};

        Object gradleRunner = ExternalUtility.invokeStaticMethod(soughtClass, "createGradleRunner", argumentClasses, gradleHomeDirectory, interaction, showDebugInfo);
        return (GradleRunnerVersion1) gradleRunner;
    }

    /**
     * This function uses an early way (early 0.9 pre-release and sooner) of instantiating the GradleRunner and should no longer be used. It unfortunately is tied to a single wrapper class instance
     * (which it tries to directly instantiate). This doesn't allow the GradleRunner to adaptively determine what to instantiate.
     */
    private static GradleRunnerVersion1 createGradleRunnerOldWay(ClassLoader parentClassLoader, File gradleHomeDirectory, GradleRunnerInteractionVersion1 interaction, boolean showDebugInfo)
            throws Exception {
        ClassLoader bootStrapClassLoader = ExternalUtility.getGradleClassloader(parentClassLoader, gradleHomeDirectory, showDebugInfo);
        Thread.currentThread().setContextClassLoader(bootStrapClassLoader);

        //load the class in gradle that wraps our return interface and handles versioning issues.
        Class soughtClass = null;
        try {
            soughtClass = bootStrapClassLoader.loadClass("org.gradle.openapi.wrappers.runner.GradleRunnerWrapper");
        } catch (NoClassDefFoundError e) {  //might be a version mismatch
            e.printStackTrace();
            return null;
        } catch (ClassNotFoundException e) {  //might be a version mismatch
            e.printStackTrace();
            return null;
        }

        if (soughtClass == null) {
            return null;
        }

        //instantiate it.
        Constructor constructor = null;
        try {
            constructor = soughtClass.getDeclaredConstructor(File.class, GradleRunnerInteractionVersion1.class);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
            System.out.println("Dumping available constructors on " + soughtClass.getName() + "\n" + ExternalUtility.dumpConstructors(soughtClass));

            throw e;
        }

        Object gradleRunner = constructor.newInstance(gradleHomeDirectory, interaction);

        return (GradleRunnerVersion1) gradleRunner;
    }
}
