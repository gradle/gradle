/*
 * Copyright 2007-2009 the original author or authors.
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

// Copyright 2003-2010 Christian d'Heureuse, Inventec Informatik AG, Zurich, Switzerland
// www.source-code.biz, www.inventec.ch/chdh
//
// This module is multi-licensed and may be used under the terms
// of any of the following licenses:
//
//  EPL, Eclipse Public License, V1.0 or later, http://www.eclipse.org/legal
//  LGPL, GNU Lesser General Public License, V2.1 or later, http://www.gnu.org/licenses/lgpl.html
//  GPL, GNU General Public License, V2 or later, http://www.gnu.org/licenses/gpl.html
//  AL, Apache License, V2.0 or later, http://www.apache.org/licenses
//  BSD, BSD License, http://www.opensource.org/licenses/bsd-license.php
//  MIT, MIT License, http://www.opensource.org/licenses/MIT
//
// Please contact the author if you need another license.
// This module is provided "as is", without warranties of any kind.

package org.gradle.wrapper.biz.sourcecode.base64coder;

// Tests for the Base64Coder class.

import static org.junit.Assert.fail;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import org.junit.Test;

public class Base64CoderTest {

   // Test Base64Coder with constant strings.
   @Test
   public void test1() {
      check ("Aladdin:open sesame", "QWxhZGRpbjpvcGVuIHNlc2FtZQ==");  // example from RFC 2617
      check ("", "");
      check ("1", "MQ==");
      check ("22", "MjI=");
      check ("333", "MzMz");
      check ("4444", "NDQ0NA==");
      check ("55555", "NTU1NTU=");
      check ("abc:def", "YWJjOmRlZg=="); }

   private static void check (String plainText, String base64Text) {
      String s1 = Base64Coder.encodeString(plainText);
      String s2 = Base64Coder.decodeString(base64Text);
      if (!s1.equals(base64Text) || !s2.equals(plainText)) {
         fail ("Check failed for \""+plainText+"\" / \""+base64Text+"\"."); 
      }
   }

   // Test Base64Coder against sun.misc.BASE64Encoder/Decoder with random data.
   // Line length below 76.
   @Test
   public void test2() throws Exception {
      try {
         Class sunEncoderClass = Class.forName("sun.misc.BASE64Encoder");
         Class sunDecoderClass = Class.forName("sun.misc.BASE64Decoder");

         final int maxLineLen = 76 - 1;                          // the Sun encoder adds a CR/LF when a line is longer
         final int maxDataBlockLen = (maxLineLen*3) / 4;
         def sunEncoder = sunEncoderClass.newInstance();
         def sunDecoder = sunDecoderClass.newInstance();
         Random rnd = new Random(0x538afb92);
         for (int i=0; i<50000; i++) {
            int len = rnd.nextInt(maxDataBlockLen+1);
            byte[] b0 = new byte[len];
            rnd.nextBytes(b0);
            String e1 = new String(Base64Coder.encode(b0));
            String e2 = sunEncoder.encode(b0);
            assertEquals (e2, e1);
            byte[] b1 = Base64Coder.decode(e1);
            byte[] b2 = sunDecoder.decodeBuffer(e2);
            assertArrayEquals (b0, b1);
            assertArrayEquals (b0, b2); 
         }      
      } catch (ClassNotFoundException e) {
         // JDK does not support sun.misc.BASE64Encoder ignore test
      }   
   }

   // Test Base64Coder line encoding/decoding against sun.misc.BASE64Encoder/Decoder
   // with random data.
   @Test
   public void test3() throws Exception {
      try {
         Class sunEncoderClass = Class.forName("sun.misc.BASE64Encoder");
         Class sunDecoderClass = Class.forName("sun.misc.BASE64Decoder");

         final int maxDataBlockLen = 512;
         def sunEncoder = sunEncoderClass.newInstance();
         def sunDecoder = sunDecoderClass.newInstance();
         Random rnd = new Random(0x39ac7d6e);
         for (int i=0; i<10000; i++) {
            int len = rnd.nextInt(maxDataBlockLen+1);
            byte[] b0 = new byte[len];
            rnd.nextBytes(b0);
            String e1 = new String(Base64Coder.encodeLines(b0));
            String e2 = sunEncoder.encodeBuffer(b0);
            assertEquals (e2, e1);
            byte[] b1 = Base64Coder.decodeLines(e1);
            byte[] b2 = sunDecoder.decodeBuffer(e2);
            assertArrayEquals (b0, b1);
            assertArrayEquals (b0, b2); 
         }
      } catch (ClassNotFoundException e) {
         // JDK does not support sun.misc.BASE64Encoder ignore test
      }

   }

} // end class TestBase64Coder