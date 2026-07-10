/*
 * Copyright 2020 the original author or authors.
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
package gradlebuild.docs;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class XslTransformer {
    public static void main(String[] args) throws TransformerException, IOException {
        if (args.length < 3 || args.length > 4) {
            throw new IllegalArgumentException("USAGE: <style-sheet> <source-file> <dest-file> [dest-dir]");
        }
        File stylesheet = new File(args[0]);
        File source = new File(args[1]);
        File dest = new File(args[2]);
        String destDir = "";
        if (args.length > 3) {
            destDir = args[3];
        }

        System.out.format("=> stylesheet %s%n", stylesheet);
        System.out.format("=> source %s%n", source);
        System.out.format("=> dest %s%n", dest);
        System.out.format("=> destDir %s%n", destDir);

        TransformerFactory factory = TransformerFactory.newInstance();
        Transformer transformer = factory.newTransformer(new StreamSource(stylesheet));

        System.out.format("=> transformer %s (%s)%n", transformer, transformer.getClass().getName());

        if (destDir.length() > 0) {
            transformer.setParameter("base.dir", destDir + "/");
        }
        FileOutputStream outstr = new FileOutputStream(dest);
        try {
            transformer.transform(new StreamSource(source), new StreamResult(outstr));
        } finally {
            outstr.close();
        }
    }
}
