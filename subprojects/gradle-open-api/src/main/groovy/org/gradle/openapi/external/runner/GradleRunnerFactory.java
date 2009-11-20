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

 This provides a simple way to execute gradle commands from an external
 process. call createGradleRunner to instantiate a gradle runner. You can then
 use that to execute commands.

 @author mhunsicker
  */
public class GradleRunnerFactory
{
   /*
      Call this to instante an object that you can use to execute gradle
      commands directly.

      Note: this function is meant to be backward and forward compatible. So
      this signature should not change at all, however, it may take and return
      objects that implement ADDITIONAL interfaces. That is, it will always
      return a GradleRunnerVersion1, but it may also be an object that implements
      GradleRunnerVersion2 (notice the 2). The caller will need to dynamically
      determine that. The GradleRunnerInteractionVersion1 may take an object
      that also implements GradleRunnerInteractionVersion2. If so, we'll
      dynamically determine that and handle it. Of course, this all depends on
      what happens in the future.

      @param  parentClassLoader    Your classloader. Probably the classloader
                                   of whatever class is calling this.
      @param  gradleHomeDirectory  the root directory of a gradle installation
      @param  interaction          this is how we interact with the caller.
      @param  showDebugInfo        true to show some additional information that
                                   may be helpful diagnosing problems is this
                                   fails
      @return a gradle runner
      @author mhunsicker
   */
   public static GradleRunnerVersion1 createGradleRunner( ClassLoader parentClassLoader, File gradleHomeDirectory, GradleRunnerInteractionVersion1 interaction, boolean showDebugInfo ) throws Exception
   {
      ClassLoader bootStrapClassLoader = ExternalUtility.getGradleClassloader( parentClassLoader, gradleHomeDirectory, showDebugInfo );
      Thread.currentThread().setContextClassLoader(bootStrapClassLoader);

      //load the class in gradle that wraps our return interface and handles versioning issues.
      Class soughtClass = null;
      try
      {
         soughtClass = bootStrapClassLoader.loadClass( "org.gradle.openapi.wrappers.runner.GradleRunnerWrapper" );
      }
      catch( NoClassDefFoundError e )
      {  //might be a version mismatch
         e.printStackTrace();
         return null;
      }
      catch( ClassNotFoundException e )
      {  //might be a version mismatch
         e.printStackTrace();
         return null;
      }

      if( soughtClass == null ) {
         return null;
      }

      //instantiate it.
      Constructor constructor = null;
      try
      {
         constructor = soughtClass.getDeclaredConstructor( File.class, GradleRunnerInteractionVersion1.class );
      }
      catch( NoSuchMethodException e )
      {
         e.printStackTrace();
         System.out.println( "Dumping available constructors on " + soughtClass.getName() + "\n" + ExternalUtility.dumpConstructors( soughtClass ) );

         throw e;
      }

      Object gradleRunner = constructor.newInstance( gradleHomeDirectory, interaction );

      return (GradleRunnerVersion1) gradleRunner;
   }
}
