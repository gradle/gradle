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

/*
 This loads up the main gradle UI. This is intended to be used as a plugin
 inside another application (like an IDE) in a dynamic fashion. If you're
 always going to ship the entire plugin with the entire Gradle dist, you don't
 need to use this. This is meant to dynamically load Gradle from its dist. The
 idea is that you point your plugin to a Gradle dist and then can always load
 the latest version.

 @author mhunsicker
  */
public class UIFactory
{
   /*
      Call this to instante a self-contained gradle UI. That is, everything in
      the UI is in a single panel (versus 2 panels one for the tasks and one
      for the output). This will load gradle via reflection, instantiate the UI
      and all required gradle-related classes.

      Note: this function is meant to be backward and forward compatible. So
      this signature should not change at all, however, it may take and return
      objects that implement ADDITIONAL interfaces. That is, it will always
      return SinglePaneUIVersion1, but it may also be an object that implements
      SinglePaneUIVersion2 (notice the 2). The caller will need to dynamically
      determine that. The SinglePaneUIInteractionVersion1 may take an object
      that also implements SinglePaneUIInteractionVersion2. If so, we'll
      dynamically determine that and handle it. Of course, this all depends on
      what happens in the future.
      @param  parentClassLoader    Your classloader. Probably the classloader
                                   of whatever class is calling this.
      @param  gradleHomeDirectory  the root directory of a gradle installation
      @param  singlePaneUIArguments this is how we interact with the caller.
      @param  showDebugInfo        true to show some additional information that
                                   may be helpful diagnosing problems is this
                                   fails
      @return the UI object.
      @author mhunsicker
   */
   public static SinglePaneUIVersion1 createUI( ClassLoader parentClassLoader, File gradleHomeDirectory, final SinglePaneUIInteractionVersion1 singlePaneUIArguments, boolean showDebugInfo ) throws Exception
   {
      ClassLoader bootStrapClassLoader = ExternalUtility.getGradleClassloader( parentClassLoader, gradleHomeDirectory, showDebugInfo );
      Thread.currentThread().setContextClassLoader(bootStrapClassLoader);

      //load the class in gradle that wraps our return interface and handles versioning issues.
      Class soughtClass = null;
      try
      {
         soughtClass = bootStrapClassLoader.loadClass( "org.gradle.openapi.wrappers.ui.OpenAPIUIWrapper" );
      }
      catch( NoClassDefFoundError e )
      {  //might be a version mismatch
         e.printStackTrace();
         return null;
      }
      catch( ClassNotFoundException e )
      {  //might be a version mismatch
         e.printStackTrace();
      }
      if( soughtClass == null )
         return null;

      //instantiate it.
      Constructor constructor = null;
      try
      {
         constructor = soughtClass.getDeclaredConstructor( SettingsNodeVersion1.class, AlternateUIInteractionVersion1.class );
      }
      catch( NoSuchMethodException e )
      {
         e.printStackTrace();
         System.out.println( "Dumping available constructors on " + soughtClass.getName() + "\n" + ExternalUtility.dumpConstructors( soughtClass ) );

         throw e;
      }
      Object singlePaneUI = constructor.newInstance( singlePaneUIArguments.instantiateSettings(), singlePaneUIArguments.instantiateAlternateUIInteraction() );
      return (SinglePaneUIVersion1) singlePaneUI;
   }

}
