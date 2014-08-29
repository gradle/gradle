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

package org.gradle.api.plugins.jetty.internal;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Iterator;
import java.util.List;

import org.mortbay.jetty.plus.annotation.InjectionCollection;
import org.mortbay.jetty.plus.annotation.LifeCycleCallbackCollection;
import org.mortbay.jetty.plus.annotation.RunAsCollection;
import org.mortbay.jetty.plus.webapp.Configuration;
import org.mortbay.jetty.servlet.FilterHolder;
import org.mortbay.jetty.servlet.ServletHolder;
import org.mortbay.jetty.webapp.WebAppContext;
import org.mortbay.jetty.webapp.WebAppClassLoader;
import org.mortbay.log.Log;
import org.mortbay.util.LazyList;

public class JettyConfiguration extends Configuration {
    private List<File> classPathFiles;
    private File webXmlFile;

    public JettyConfiguration() {
        super();
    }

    public void setClassPathConfiguration(List<File> classPathFiles) {
        this.classPathFiles = classPathFiles;
    }

    public void setWebXml(File webXmlFile) {
        this.webXmlFile = webXmlFile;
    }

    /**
     * Set up the classloader for the webapp, using the various parts of the Maven project
     *
     * @see org.mortbay.jetty.webapp.Configuration#configureClassLoader()
     */
    public void configureClassLoader() throws Exception {
        if (classPathFiles != null) {
            Log.debug("Setting up classpath ...");

            //put the classes dir and all dependencies into the classpath
            for (File classPathFile : classPathFiles) {
                ((WebAppClassLoader) getWebAppContext().getClassLoader()).addClassPath(
                        classPathFile.getCanonicalPath());
            }

            if (Log.isDebugEnabled()) {
                Log.debug("Classpath = " + LazyList.array2List(
                        ((URLClassLoader) getWebAppContext().getClassLoader()).getURLs()));
            }
        } else {
            super.configureClassLoader();
        }
    }

    protected URL findWebXml() throws IOException {
        //if an explicit web.xml file has been set (eg for jetty:run) then use it
        if (webXmlFile != null && webXmlFile.exists()) {
            return webXmlFile.toURI().toURL();
        }

        //if we haven't overridden location of web.xml file, use the
        //standard way of finding it
        Log.debug("Looking for web.xml file in WEB-INF");
        return super.findWebXml();
    }

    public void parseAnnotations() throws Exception {
        String v = System.getProperty("java.version");
        String[] version = v.split("\\.");
        if (version == null) {
            Log.info("Unable to determine jvm version, annotations will not be supported");
            return;
        }
        int major = Integer.parseInt(version[0]);
        int minor = Integer.parseInt(version[1]);
        if ((major >= 1) && (minor >= 5)) {
            //TODO it would be nice to be able to re-use the parseAnnotations() method on 
            //the org.mortbay.jetty.annotations.Configuration class, but it's too difficult?

            //able to use annotations on jdk1.5 and above
            Class<?> annotationParserClass = Thread.currentThread().getContextClassLoader().loadClass(
                    "org.mortbay.jetty.annotations.AnnotationParser");
            Method parseAnnotationsMethod = annotationParserClass.getMethod("parseAnnotations", WebAppContext.class,
                    Class.class, RunAsCollection.class, InjectionCollection.class, LifeCycleCallbackCollection.class);

            //look thru _servlets
            Iterator itor = LazyList.iterator(_servlets);
            while (itor.hasNext()) {
                ServletHolder holder = (ServletHolder) itor.next();
                Class servlet = getWebAppContext().loadClass(holder.getClassName());
                parseAnnotationsMethod.invoke(null, getWebAppContext(), servlet, _runAsCollection, _injections,
                        _callbacks);
            }

            //look thru _filters
            itor = LazyList.iterator(_filters);
            while (itor.hasNext()) {
                FilterHolder holder = (FilterHolder) itor.next();
                Class filter = getWebAppContext().loadClass(holder.getClassName());
                parseAnnotationsMethod.invoke(null, getWebAppContext(), filter, null, _injections, _callbacks);
            }

            //look thru _listeners
            itor = LazyList.iterator(_listeners);
            while (itor.hasNext()) {
                Object listener = itor.next();
                parseAnnotationsMethod.invoke(null, getWebAppContext(), listener.getClass(), null, _injections,
                        _callbacks);
            }
        } else {
            Log.info("Annotations are not supported on jvms prior to jdk1.5");
        }
    }
}
