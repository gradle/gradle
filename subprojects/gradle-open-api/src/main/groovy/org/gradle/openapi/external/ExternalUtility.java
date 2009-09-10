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
package org.gradle.openapi.external;

import java.io.File;
import java.io.FileFilter;
import java.lang.reflect.Constructor;
import org.gradle.BootstrapLoader;

/**

 Utility functions required by the OpenAPI

 @author mhunsicker
  */
public class ExternalUtility
{
   /*
      Call this to get a classloader that has loaded gradle.

      @param  parentClassLoader    Your classloader. Probably the classloader
                                   of whatever class is calling this.
      @param  gradleHomeDirectory  the root directory of a gradle installation
      @param  showDebugInfo        true to show some additional information that
                                   may be helpful diagnosing problems is this
                                   fails
      @return a classloader that has loaded gradle and all of its dependencies.
      @author mhunsicker
   */
   public static ClassLoader getGradleClassloader( ClassLoader parentClassLoader, File gradleHomeDirectory, boolean showDebugInfo ) throws Exception
   {
      File gradleJarFile = getGradleJar( gradleHomeDirectory );
      if( gradleJarFile == null )
         throw new RuntimeException( "Not a valid gradle home directory '" + gradleHomeDirectory.getAbsolutePath() + "'" );

      System.setProperty("gradle.home", gradleHomeDirectory.getAbsolutePath() );

      //the following code was used when BootstrapLoader was in Gradle-core. It was moved out due
      //a circular dependency. Hopefully that will be solved and then this probably needs to moved
      //back (due to duplication of code). When it does, this needs to uncommented out (and the code
      //below it removed)
      
      ////create a class loader that will load our bootloader.
      //URLClassLoader contextClassLoader = new URLClassLoader(new URL[] { gradleJarFile.toURI().toURL() }, parentClassLoader );
      //
      //Class bootstrapClass = contextClassLoader.loadClass("org.gradle.BootstrapLoader");
      //
      //Object loader = bootstrapClass.newInstance();
      //Method initializeMethod = bootstrapClass.getDeclaredMethod( "initialize", new Class<?>[]{ClassLoader.class, File.class, boolean.class, boolean.class, boolean.class } );
      //
      ////get the bootloader to actually load gradle since it requires some very specific steps
      //initializeMethod.invoke( loader, parentClassLoader, gradleHomeDirectory, true, false, showDebugInfo );
      //
      ////get the bootloader's classloader so we can use that to load a specific class from gradle.
      //Method getClassLoaderMethod = bootstrapClass.getDeclaredMethod( "getClassLoader" );
      //ClassLoader bootStrapClassLoader = (ClassLoader) getClassLoaderMethod.invoke( loader );

      BootstrapLoader bootstrapLoader = new BootstrapLoader();
      bootstrapLoader.initialize( parentClassLoader, gradleHomeDirectory, true, false, showDebugInfo );
      return bootstrapLoader.getClassLoader();
   }

   /*
      This locates the gradle jar. We do NOT want the gradle-wrapper jar.

      @param  gradleHomeDirectory the root directory of a gradle installation.
                                  We're expecting this to have a child directory
                                  named 'lib'.
      @return the gradle jar file. Null if we didn't find it.
      @author mhunsicker
   */
   public static File getGradleJar( File gradleHomeDirectory )
   {
      File libDirectory = new File( gradleHomeDirectory, "lib" );
      if( !libDirectory.exists() )
         return null;

      //try to get the gradle.jar. It'll be "gradle-[version].jar"
      File[] files = libDirectory.listFiles( new FileFilter()
      {
         public boolean accept( File file )
         {
            String name = file.getName();
	         if( name.startsWith( "gradle-core-" ) && name.endsWith( ".jar" ) )
               return true;
            return false;
         }
      } );

      if( files == null || files.length == 0 )
         return null;

      //if they've given us a directory with multiple gradle jars, tell them. We won't know which one to use.
      if( files.length > 1 )
           throw new RuntimeException( "Installation has multiple gradle jars. Cannot determine which one to use. Found files: " + files );

      return files[ 0 ];
   }

   //just a function to help debugging. If we can't find the constructor we want, this dumps out what is available.
   public static String dumpConstructors( Class classInQuestion )
   {
      StringBuilder builder = new StringBuilder( );
      Constructor[] constructors = classInQuestion.getConstructors();
      for( int index = 0; index < constructors.length; index++ )
      {
         Constructor constructor = constructors[index];
         builder.append( constructor ).append( '\n' );
      }

      return builder.toString();
   }
}
