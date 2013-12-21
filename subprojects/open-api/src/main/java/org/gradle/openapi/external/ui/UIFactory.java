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
package org.gradle.openapi.external.ui;

import org.gradle.openapi.external.ExternalUtility;

import java.io.File;
import java.lang.reflect.Constructor;

/**
 * This loads up the main gradle UI. This is intended to be used as a plugin inside another application (like an IDE) in a dynamic fashion. If you're always going to ship the entire plugin with the
 * entire Gradle dist, you don't need to use this. This is meant to dynamically load Gradle from its dist. The idea is that you point your plugin to a Gradle dist and then can always load the latest
 * version.
 * @deprecated No replacement
 */
@Deprecated
public class UIFactory {
    private static final String UIWRAPPER_FACTORY_CLASS_NAME = "org.gradle.openapi.wrappers.UIWrapperFactory";

    /**
     * Call this to instantiate a self-contained gradle UI. That is, everything in the UI is in a single panel (versus 2 panels one for the tasks and one for the output). This will load gradle via
     * reflection, instantiate the UI and all required gradle-related classes.
     *
     * <p>Note: this function is meant to be backward and forward compatible. So this signature should not change at all, however, it may take and return objects that implement ADDITIONAL interfaces.
     * That is, it will always return SinglePaneUIVersion1, but it may also be an object that implements SinglePaneUIVersion2 (notice the 2). The caller will need to dynamically determine that. The
     * SinglePaneUIInteractionVersion1 may take an object that also implements SinglePaneUIInteractionVersion2. If so, we'll dynamically determine that and handle it. Of course, this all depends on
     * what happens in the future.
     *
     * @param parentClassLoader Your classloader. Probably the classloader of whatever class is calling this.
     * @param gradleHomeDirectory the root directory of a gradle installation
     * @param showDebugInfo true to show some additional information that may be helpful diagnosing problems is this fails
     * @return the UI object.
     * @deprecated Use the tooling API instead.
     */
    @Deprecated
    public static SinglePaneUIVersion1 createSinglePaneUI(ClassLoader parentClassLoader, File gradleHomeDirectory, final SinglePaneUIInteractionVersion1 interaction, boolean showDebugInfo)
            throws Exception {
        //much of this function is exception handling so if we can't obtain it via the newer factory method, then
        //we'll try the old way, but we want to report the original exception if we can't do it either way.
        Exception viaFactoryException = null;
        SinglePaneUIVersion1 gradleUI = null;

        //first, try it the new way
        try {
            gradleUI = createSinglePaneUIViaFactory(parentClassLoader, gradleHomeDirectory, interaction, showDebugInfo);
        } catch (Exception e) {
            //we might ignore this. It means we're probably using an older version of gradle. That case is handled below.
            //If not, this exception will be thrown at the end.
            viaFactoryException = e;
        }

        //try it the old way
        if (gradleUI == null) {
            gradleUI = createSinglePaneUIOldWay(parentClassLoader, gradleHomeDirectory, interaction, showDebugInfo);
        }

        //if we still don't have a gradle ui and we have an exception from using the factory, throw it. If we
        //got an exception using the 'old way', it would have been thrown already and we wouldn't be here.
        if (gradleUI == null && viaFactoryException != null) {
            throw viaFactoryException;
        }

        return gradleUI;
    }

    /**
     * This function uses a factory to instantiate the UI. The factory is located with the version of gradle pointed to by gradleHomeDirectory and thus allows the version of gradle being loaded to make
     * decisions about how to instantiate the UI. This is needed as multiple versions of the UI are being used.
     */
    private static SinglePaneUIVersion1 createSinglePaneUIViaFactory(ClassLoader parentClassLoader, File gradleHomeDirectory, final SinglePaneUIInteractionVersion1 interaction, boolean showDebugInfo)
            throws Exception {
        //load the class in gradle that wraps our return interface and handles versioning issues.
        Class soughtClass = ExternalUtility.loadGradleClass(UIWRAPPER_FACTORY_CLASS_NAME, parentClassLoader, gradleHomeDirectory, showDebugInfo);
        if (soughtClass == null) {
            return null;
        }

        Class[] argumentClasses = new Class[]{SinglePaneUIInteractionVersion1.class, boolean.class};

        Object gradleUI = ExternalUtility.invokeStaticMethod(soughtClass, "createSinglePaneUI", argumentClasses, interaction, showDebugInfo);
        return (SinglePaneUIVersion1) gradleUI;
    }

    /**
     * This function uses an early way (early 0.9 pre-release and sooner) of instantiating the UI and should no longer be used. It unfortunately is tied to a single wrapper class instance (which it
     * tries to directly instantiate). This doesn't allow the Gradle UI to adaptively determine what to instantiate.
     */
    private static SinglePaneUIVersion1 createSinglePaneUIOldWay(ClassLoader parentClassLoader, File gradleHomeDirectory, final SinglePaneUIInteractionVersion1 singlePaneUIArguments,
                                                                 boolean showDebugInfo) throws Exception {
        ClassLoader bootStrapClassLoader = ExternalUtility.getGradleClassloader(parentClassLoader, gradleHomeDirectory, showDebugInfo);
        Thread.currentThread().setContextClassLoader(bootStrapClassLoader);

        //load the class in gradle that wraps our return interface and handles versioning issues.
        Class soughtClass = null;
        try {
            soughtClass = bootStrapClassLoader.loadClass("org.gradle.openapi.wrappers.ui.SinglePaneUIWrapper");
        } catch (NoClassDefFoundError e) {  //might be a version mismatch
            e.printStackTrace();
            return null;
        } catch (ClassNotFoundException e) {  //might be a version mismatch
            e.printStackTrace();
        }
        if (soughtClass == null) {
            return null;
        }

        //instantiate it.
        Constructor constructor = null;
        try {
            constructor = soughtClass.getDeclaredConstructor(SinglePaneUIInteractionVersion1.class);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
            System.out.println("Dumping available constructors on " + soughtClass.getName() + "\n" + ExternalUtility.dumpConstructors(soughtClass));

            throw e;
        }
        Object singlePaneUI = constructor.newInstance(singlePaneUIArguments);
        return (SinglePaneUIVersion1) singlePaneUI;
    }

    /**
     * Call this to instantiate a gradle UI that contains the main tab control separate from the output panel. This allows you to position the output however you like. For example: you can place the
     * main pane along the side going vertically and you can place the output pane along the bottom going horizontally. This will load gradle via reflection, instantiate the UI and all required
     * gradle-related classes.
     *
     * <p>Note: this function is meant to be backward and forward compatible. So this signature should not change at all, however, it may take and return objects that implement ADDITIONAL interfaces.
     * That is, it will always return SinglePaneUIVersion1, but it may also be an object that implements SinglePaneUIVersion2 (notice the 2). The caller will need to dynamically determine that. The
     * SinglePaneUIInteractionVersion1 may take an object that also implements SinglePaneUIInteractionVersion2. If so, we'll dynamically determine that and handle it. Of course, this all depends on
     * what happens in the future.
     *
     * @param parentClassLoader Your classloader. Probably the classloader of whatever class is calling this.
     * @param gradleHomeDirectory the root directory of a gradle installation
     * @param interaction this is how we interact with the caller.
     * @param showDebugInfo true to show some additional information that may be helpful diagnosing problems is this fails
     * @return the UI object.
     * @deprecated Use the tooling API instead.
     */
    @Deprecated
    public static DualPaneUIVersion1 createDualPaneUI(ClassLoader parentClassLoader, File gradleHomeDirectory, final DualPaneUIInteractionVersion1 interaction, boolean showDebugInfo)
            throws Exception {
        //much of this function is exception handling so if we can't obtain it via the newer factory method, then
        //we'll try the old way, but we want to report the original exception if we can't do it either way.
        Exception viaFactoryException = null;
        DualPaneUIVersion1 gradleUI = null;

        //first, try it the new way
        try {
            gradleUI = createDualPaneUIViaFactory(parentClassLoader, gradleHomeDirectory, interaction, showDebugInfo);
        } catch (Exception e) {
            //we might ignore this. It means we're probably using an older version of gradle. That case is handled below.
            //If not, this exception will be thrown at the end.
            viaFactoryException = e;
        }

        //try it the old way
        if (gradleUI == null) {
            gradleUI = createDualPaneUIOldWay(parentClassLoader, gradleHomeDirectory, interaction, showDebugInfo);
        }

        //if we still don't have a gradle ui and we have an exception from using the factory, throw it. If we
        //got an exception using the 'old way', it would have been thrown already and we wouldn't be here.
        if (gradleUI == null && viaFactoryException != null) {
            throw viaFactoryException;
        }

        return gradleUI;
    }

    /**
     * This function uses a factory to instantiate the UI. The factory is located with the version of gradle pointed to by gradleHomeDirectory and thus allows the version of gradle being loaded to make
     * decisions about how to instantiate the UI. This is needed as multiple versions of the UI are being used.
     * @deprecated Use the tooling API instead.
     */
    @Deprecated
    public static DualPaneUIVersion1 createDualPaneUIViaFactory(ClassLoader parentClassLoader, File gradleHomeDirectory, final DualPaneUIInteractionVersion1 interaction, boolean showDebugInfo)
            throws Exception {
        //load the class in gradle that wraps our return interface and handles versioning issues.
        Class soughtClass = ExternalUtility.loadGradleClass(UIWRAPPER_FACTORY_CLASS_NAME, parentClassLoader, gradleHomeDirectory, showDebugInfo);
        if (soughtClass == null) {
            return null;
        }

        Class[] argumentClasses = new Class[]{DualPaneUIInteractionVersion1.class, boolean.class};

        Object gradleUI = ExternalUtility.invokeStaticMethod(soughtClass, "createDualPaneUI", argumentClasses, interaction, showDebugInfo);
        return (DualPaneUIVersion1) gradleUI;
    }

    /**
     * This function uses an early way (early 0.9 pre-release and sooner) of instantiating the UI and should no longer be used. It unfortunately is tied to a single wrapper class instance (which it
     * tries to directly instantiate). This doesn't allow the Gradle UI to adaptively determine what to instantiate.
     */
    private static DualPaneUIVersion1 createDualPaneUIOldWay(ClassLoader parentClassLoader, File gradleHomeDirectory, final DualPaneUIInteractionVersion1 interaction, boolean showDebugInfo)
            throws Exception {
        ClassLoader bootStrapClassLoader = ExternalUtility.getGradleClassloader(parentClassLoader, gradleHomeDirectory, showDebugInfo);
        Thread.currentThread().setContextClassLoader(bootStrapClassLoader);

        //load the class in gradle that wraps our return interface and handles versioning issues.
        Class soughtClass = null;
        try {
            soughtClass = bootStrapClassLoader.loadClass("org.gradle.openapi.wrappers.ui.DualPaneUIWrapper");
        } catch (NoClassDefFoundError e) {  //might be a version mismatch
            e.printStackTrace();
            return null;
        } catch (ClassNotFoundException e) {  //might be a version mismatch
            e.printStackTrace();
        }
        if (soughtClass == null) {
            return null;
        }

        //instantiate it.
        Constructor constructor = null;
        try {
            constructor = soughtClass.getDeclaredConstructor(DualPaneUIInteractionVersion1.class);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
            System.out.println("Dumping available constructors on " + soughtClass.getName() + "\n" + ExternalUtility.dumpConstructors(soughtClass));

            throw e;
        }
        Object gradleUI = constructor.newInstance(interaction);
        return (DualPaneUIVersion1) gradleUI;
    }
}
