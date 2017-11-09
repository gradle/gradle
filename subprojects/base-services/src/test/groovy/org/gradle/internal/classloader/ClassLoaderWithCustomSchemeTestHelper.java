/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.internal.classloader;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;

/**
 * Taken from SO: https://stackoverflow.com/a/26409796
 */
public class ClassLoaderWithCustomSchemeTestHelper {

  public static String sCUSTOMURI = "customuri";

  protected static class CustomURLConnection extends URLConnection {

    CustomURLConnection(URL url) {
      super(url);
    }

    @Override
    public void connect() throws IOException {
        System.out.println("Connected!");
    }
  }

  protected static class CustomURLStreamHandler extends URLStreamHandler {

    @Override
    protected URLConnection openConnection(URL url) throws IOException {
      return new CustomURLConnection(url);
    }
  }

  protected static class CustomURLStreamHandlerFactory implements URLStreamHandlerFactory {

    @Override
    public URLStreamHandler createURLStreamHandler(String protocol) {
      if (sCUSTOMURI.equals(protocol)) {
        return new CustomURLStreamHandler();
      }

      return null;
    }

  }

}
