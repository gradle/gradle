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

import java.io.File;
import java.util.List;

/*
 This is how the gradle UI panel interacts with the UI that is holding it.

 This is a mirror of AlternateUIInteraction inside Gradle, but this is meant
 to aid backward and forward compatibility by shielding you from direct
 changes within gradle.

 @author mhunsicker
 */
public interface AlternateUIInteractionVersion1
{
   /*
      This is called when we should edit the specified files. Open them in the
      current IDE or some external editor.

      @param  files      the files to open
      @author mhunsicker
   */
   public void editFiles( List<File> files );

   /*
      Determines if we can call editFiles. This is not a dynamic answer and
      should always return either true of false. If you want to change the
      answer, return true and then handle the files differently in editFiles.
      @return true if support editing files, false otherwise.
      @author mhunsicker
   */
   public boolean doesSupportEditingFiles();

   /**
    Notification that a command is about to be executed. This is mostly useful
    for IDE's that may need to save their files.

    @param fullCommandLine the command that's about to be executed.
    @author mhunsicker
    */
   public void aboutToExecuteCommand( String fullCommandLine );
}
