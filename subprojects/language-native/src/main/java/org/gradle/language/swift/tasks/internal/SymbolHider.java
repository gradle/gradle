/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.language.swift.tasks.internal;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;


/**
 * Parse and hide given symbols in on object file based on COFF format documented
 * here: https://docs.microsoft.com/en-us/windows/desktop/debug/pe-format
 */
public class SymbolHider {
    DataReader data;
    private byte[] objectBytes;

    private static class DataReader {
        private byte[] dataBytes;
        private int position = 0;

        public DataReader(byte[] dataBytes) {
            this.dataBytes = dataBytes;
        }

        public int getPosition() {
            return position;
        }

        public void moveTo(int position) {
            this.position = position;
        }

        public int readByte() {
            return Byte.toUnsignedInt(dataBytes[position++]);
        }

        public int readWord() {
            return readByte() | readByte() << 8;
        }

        public int readDoubleWord() {
            return readByte() | readByte() << 8 | readByte() << 16 | readByte() << 24;
        }

        public byte[] readBytes(int count) {
            byte[] sub = Arrays.copyOfRange(dataBytes, position, position + count);
            position += count;
            return sub;
        }
    }

    private static class COFFHeader {
        public int machine;
        public int numberOfSections;
        public int timeDateStamp;
        public int pointerToSymbolTable;
        public int numberOfSymbols;
        public int sizeOfOptionalHeader;
        public int characteristics;

        public COFFHeader(DataReader data) {
            machine = data.readWord();
            numberOfSections = data.readWord();
            timeDateStamp = data.readDoubleWord();
            pointerToSymbolTable = data.readDoubleWord();
            numberOfSymbols = data.readDoubleWord();
            sizeOfOptionalHeader = data.readWord();
            characteristics = data.readWord();
        }
    }

    private static class SymbolRecord {
        private int storageClass;
        private byte[] name;
        private int value;
        private int sectionNumber;
        private int type;
        private int numberOfAuxSymbols;

        public SymbolRecord(DataReader data) {
            name = data.readBytes(8);
            value = data.readDoubleWord();
            sectionNumber = data.readWord();
            type = data.readWord();
            storageClass = data.readByte();
            numberOfAuxSymbols = data.readByte();
        }

        public String getName() {
            // We only need to hide "main", so only support short named symbols here.
            int nullCharIndex = 0;
            for (nullCharIndex = 0; nullCharIndex < name.length; ++nullCharIndex) {
                if (name[nullCharIndex] == 0) {
                    break;
                }
            }
            return new String(name, 0, nullCharIndex, StandardCharsets.UTF_8);
        }
    }

    private static class SymbolTable {
        private int numberOfSymbols;
        private DataReader data;
        private byte[] objectBytes;
        private static final int IMAGE_SYM_CLASS_STATIC = 0x3;

        public SymbolTable(int numberOfSymbols, DataReader data, byte[] objectBytes) {
            this.numberOfSymbols = numberOfSymbols;
            this.data = data;
            this.objectBytes = objectBytes;
        }

        public void hideSymbol(String symbolToHide) {
            for (int i = 0; i < numberOfSymbols; ++i) {
                SymbolRecord symbol = new SymbolRecord(data);
                String name = symbol.getName();

                if (name.equals(symbolToHide)) {
                    objectBytes[data.getPosition() - 2] = IMAGE_SYM_CLASS_STATIC;
                    break;
                }
            }
        }
    }

    public SymbolHider(File inputFile) throws IOException {
        objectBytes = Files.readAllBytes(Paths.get(inputFile.getAbsolutePath()));
    }

    public void hideSymbol(String symbolToHide) {
        data = new DataReader(objectBytes);
        COFFHeader coffHeader = new COFFHeader(data);

        data.moveTo(coffHeader.pointerToSymbolTable);
        SymbolTable symbolTable = new SymbolTable(coffHeader.numberOfSymbols, data, objectBytes);
        symbolTable.hideSymbol(symbolToHide);
    }

    public void saveTo(File outputFile) throws IOException {
        Files.write(Paths.get(outputFile.getAbsolutePath()), objectBytes);
    }
}
