/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.wrapper;

import java.net.URL;
import java.net.URLConnection;
import java.io.*;

/**
 * @author Hans Dockter
 */
class Download implements IDownload {
    public void download(String address, File destination) throws Exception {
        if (destination.exists()) {
            return;
        }
        destination.getParentFile().mkdirs();
        
        downloadInternal(address, destination);
    }

    private void downloadInternal(String address, File destination) throws Exception {
            OutputStream out = null;
            URLConnection conn = null;
            InputStream  in = null;
            try {
                URL url = new URL(address);
                out = new BufferedOutputStream(
                    new FileOutputStream(destination));
                conn = url.openConnection();
                in = conn.getInputStream();
                byte[] buffer = new byte[1024];
                int numRead;
                while ((numRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, numRead);
                }
            } catch (Exception e) {
                throw e;
            } finally {
                try {
                    if (in != null) {
                        in.close();
                    }
                    if (out != null) {
                        out.close();
                    }
                } catch (IOException ioe) {
                    throw ioe;
                }
            }
        }


    
}
