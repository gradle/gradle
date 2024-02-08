/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.nativeplatform.platform.internal

import org.gradle.nativeplatform.fixtures.binaryinfo.ReadelfBinaryInfo

import spock.lang.Specification
import spock.lang.Unroll

class ReadelfBinaryInfoTest extends Specification {
    def "read list of files from readelf"() {
        when:
        def input = """
Symbol table '.symtab' contains 53 entries:
   Num:    Value          Size Type    Bind   Vis      Ndx Name
     0: 0000000000000000     0 NOTYPE  LOCAL  DEFAULT  UND
     1: 0000000000000000     0 FILE    LOCAL  DEFAULT  ABS crt1.o
     2: 000000000040037c    32 OBJECT  LOCAL  DEFAULT    4 __abi_tag
     3: 0000000000000000     0 FILE    LOCAL  DEFAULT  ABS greeter.cpp
     4: 00000000004010e0    11 FUNC    LOCAL  DEFAULT   15 _GLOBAL__sub_I_g[...]
     5: 0000000000404191     1 OBJECT  LOCAL  DEFAULT   26 _ZStL8__ioinit
     6: 00000000004010a0    59 FUNC    LOCAL  DEFAULT   15 __cxx_global_var_init
     7: 0000000000000000     0 FILE    LOCAL  DEFAULT  ABS main.cpp
     8: 0000000000401130    11 FUNC    LOCAL  DEFAULT   15 _GLOBAL__sub_I_m[...]
     9: 0000000000404192     1 OBJECT  LOCAL  DEFAULT   26 _ZStL8__ioinit
    10: 00000000004010f0    59 FUNC    LOCAL  DEFAULT   15 __cxx_global_var_init
    11: 0000000000000000     0 FILE    LOCAL  DEFAULT  ABS crtstuff.c
    12: 0000000000401180     0 FUNC    LOCAL  DEFAULT   15 deregister_tm_clones
    13: 00000000004011b0     0 FUNC    LOCAL  DEFAULT   15 register_tm_clones
    14: 00000000004011f0     0 FUNC    LOCAL  DEFAULT   15 __do_global_dtors_aux
    15: 0000000000404190     1 OBJECT  LOCAL  DEFAULT   26 completed.0
    16: 0000000000403dd8     0 OBJECT  LOCAL  DEFAULT   21 __do_global_dtor[...]
    17: 0000000000401220     0 FUNC    LOCAL  DEFAULT   15 frame_dummy
    18: 0000000000403dc0     0 OBJECT  LOCAL  DEFAULT   20 __frame_dummy_in[...]
    19: 0000000000000000     0 FILE    LOCAL  DEFAULT  ABS sum.cpp
    20: 0000000000000000     0 FILE    LOCAL  DEFAULT  ABS multiply.cpp
    21: 0000000000000000     0 FILE    LOCAL  DEFAULT  ABS crtstuff.c
    22: 00000000004021e0     0 OBJECT  LOCAL  DEFAULT   19 __FRAME_END__
    23: 0000000000000000     0 FILE    LOCAL  DEFAULT  ABS
    24: 0000000000402014     0 NOTYPE  LOCAL  DEFAULT   18 __GNU_EH_FRAME_HDR
    25: 0000000000403de0     0 OBJECT  LOCAL  DEFAULT   22 _DYNAMIC
    26: 0000000000404000     0 OBJECT  LOCAL  DEFAULT   24 _GLOBAL_OFFSET_TABLE_
    27: 0000000000401300    23 FUNC    GLOBAL DEFAULT   15 _ZN8Multiply8mul[...]
    28: 0000000000404060     0 NOTYPE  GLOBAL DEFAULT   25 _edata
    29: 0000000000404050     0 NOTYPE  WEAK   DEFAULT   25 data_start
    30: 0000000000402000     4 OBJECT  GLOBAL DEFAULT   17 _IO_stdin_used
    31: 0000000000401290    99 FUNC    GLOBAL DEFAULT   15 main
    32: 0000000000401030     0 FUNC    GLOBAL DEFAULT  UND _ZSt4endlIcSt11c[...]
    33: 0000000000401230    22 FUNC    GLOBAL DEFAULT   15 _ZN3Sum3sumEii
    34: 0000000000404058     0 OBJECT  GLOBAL HIDDEN    25 __dso_handle
    35: 0000000000401318     0 FUNC    GLOBAL HIDDEN    16 _fini
    36: 0000000000000000     0 FUNC    GLOBAL DEFAULT  UND __libc_start_mai[...]
    37: 0000000000401170     5 FUNC    GLOBAL HIDDEN    15 _dl_relocate_sta[...]
    38: 0000000000401250    61 FUNC    GLOBAL DEFAULT   15 _ZN7Greeter8sayH[...]
    39: 0000000000000000     0 FUNC    GLOBAL DEFAULT  UND __cxa_atexit@GLI[...]
    40: 0000000000401140    38 FUNC    GLOBAL DEFAULT   15 _start
    41: 0000000000000000     0 FUNC    GLOBAL DEFAULT  UND _ZStlsISt11char_[...]
    42: 0000000000000000     0 FUNC    GLOBAL DEFAULT  UND _ZNSolsEPFRSoS_E[...]
    43: 0000000000401000     0 FUNC    GLOBAL HIDDEN    13 _init
    44: 0000000000404060     0 OBJECT  GLOBAL HIDDEN    25 __TMC_END__
    45: 0000000000404080   272 OBJECT  GLOBAL DEFAULT   26 _ZSt4cout@GLIBCXX_3.4
    46: 0000000000404050     0 NOTYPE  GLOBAL DEFAULT   25 __data_start
    47: 0000000000404198     0 NOTYPE  GLOBAL DEFAULT   26 _end
    48: 0000000000404060     0 NOTYPE  GLOBAL DEFAULT   26 __bss_start
    49: 0000000000000000     0 FUNC    GLOBAL DEFAULT  UND _ZNSt8ios_base4I[...]
    50: 0000000000000000     0 FUNC    GLOBAL DEFAULT  UND _ZNSolsEi@GLIBCXX_3.4
    51: 0000000000000000     0 NOTYPE  WEAK   DEFAULT  UND __gmon_start__
    52: 0000000000401090     0 FUNC    GLOBAL DEFAULT  UND _ZNSt8ios_base4I[...]
"""
        def inputLines = input.readLines()
        def symbols = ReadelfBinaryInfo.readSymbols(inputLines)

        then:
        symbols.size() == 4
        symbols*.name.containsAll(['multiply.cpp', 'sum.cpp', 'greeter.cpp', 'main.cpp'])
    }

    @Unroll("reads soname value with #language readelf output")
    def "reads soname value"() {
        when:
        def inputLines = input.readLines()

        then:
        ReadelfBinaryInfo.readSoName(inputLines) == 'heythere'

        where:
        [language, input] << [
            ["English", """
Dynamic section at offset 0xdf8 contains 24 entries:
  Tag        Type                         Name/Value
 0x0000000000000001 (NEEDED)             Shared library: [libstdc++.so.6]
 0x0000000000000001 (NEEDED)             Shared library: [libm.so.6]
 0x0000000000000001 (NEEDED)             Shared library: [libgcc_s.so.1]
 0x0000000000000001 (NEEDED)             Shared library: [libc.so.6]
 0x000000000000000e (SONAME)             Library soname: [heythere]
 0x000000000000000c (INIT)               0x668
"""],
            ["German", """
Dynamische Sektion an Offset 0x1a6da8 enthält 26 Einträge:
  Tag       Typ                          Name/Wert
 0x00000001 (NEEDED)                     Gemeinsame Bibliothek [ld-linux.so.2]
 0x0000000e (SONAME)                     soname der Bibliothek: [heythere]
 0x0000000c (INIT)                       0x198c0
 0x00000019 (INIT_ARRAY)                 0x1a61e8
 0x0000001b (INIT_ARRAYSZ)               12 (Bytes)
 0x00000004 (HASH)                       0x1a1b34
 0x6ffffef5 (GNU_HASH)                   0x1b8
 0x00000005 (STRTAB)                     0xd438
 0x00000006 (SYMTAB)                     0x3ec8
 0x0000000a (STRSZ)                      23846 (Bytes)
 0x0000000b (SYMENT)                     16 (Bytes)
 0x00000003 (PLTGOT)                     0x1a8000
 0x00000002 (PLTRELSZ)                   96 (Bytes)
 0x00000014 (PLTREL)                     REL
 0x00000017 (JMPREL)                     0x172e8
 0x00000011 (REL)                        0x148d8
 0x00000012 (RELSZ)                      10768 (Bytes)
 0x00000013 (RELENT)                     8 (Bytes)
 0x6ffffffc (VERDEF)                     0x1440c
 0x6ffffffd (VERDEFNUM)                  33
 0x0000001e (FLAGS)                      STATIC_TLS
 0x6ffffffe (VERNEED)                    0x14898
 0x6fffffff (VERNEEDNUM)                 1
 0x6ffffff0 (VERSYM)                     0x1315e
 0x6ffffffa (RELCOUNT)                   1253
 0x00000000 (NULL)                       0x0
"""
            ]]
    }

    @Unroll("returns null for no soname value with #language readelf output")
    def "returns null for no soname value"() {
        when:
        def inputLines = input.readLines()

        then:
        ReadelfBinaryInfo.readSoName(inputLines) == null

        where:
        [language, input] << [
            ["English", """
Dynamic section at offset 0xdf8 contains 24 entries:
  Tag        Type                         Name/Value
 0x0000000000000001 (NEEDED)             Shared library: [libstdc++.so.6]
 0x0000000000000001 (NEEDED)             Shared library: [libm.so.6]
 0x0000000000000001 (NEEDED)             Shared library: [libgcc_s.so.1]
 0x0000000000000001 (NEEDED)             Shared library: [libc.so.6]
 0x000000000000000c (INIT)               0x668
"""],
            ["German", """
Dynamische Sektion an Offset 0x1a6da8 enthält 26 Einträge:
  Tag       Typ                          Name/Wert
 0x00000001 (NEEDED)                     Gemeinsame Bibliothek [ld-linux.so.2]
 0x0000000c (INIT)                       0x198c0
 0x00000019 (INIT_ARRAY)                 0x1a61e8
 0x0000001b (INIT_ARRAYSZ)               12 (Bytes)
 0x00000004 (HASH)                       0x1a1b34
 0x6ffffef5 (GNU_HASH)                   0x1b8
 0x00000005 (STRTAB)                     0xd438
 0x00000006 (SYMTAB)                     0x3ec8
 0x0000000a (STRSZ)                      23846 (Bytes)
 0x0000000b (SYMENT)                     16 (Bytes)
 0x00000003 (PLTGOT)                     0x1a8000
 0x00000002 (PLTRELSZ)                   96 (Bytes)
 0x00000014 (PLTREL)                     REL
 0x00000017 (JMPREL)                     0x172e8
 0x00000011 (REL)                        0x148d8
 0x00000012 (RELSZ)                      10768 (Bytes)
 0x00000013 (RELENT)                     8 (Bytes)
 0x6ffffffc (VERDEF)                     0x1440c
 0x6ffffffd (VERDEFNUM)                  33
 0x0000001e (FLAGS)                      STATIC_TLS
 0x6ffffffe (VERNEED)                    0x14898
 0x6fffffff (VERNEEDNUM)                 1
 0x6ffffff0 (VERSYM)                     0x1315e
 0x6ffffffa (RELCOUNT)                   1253
 0x00000000 (NULL)                       0x0
"""
            ]]
    }

    @Unroll("reads architecture value with #language readelf output")
    def "reads architecture value"() {
        when:
        def inputLines = input.readLines()

        then:
        ReadelfBinaryInfo.readArch(inputLines).isI386() == true

        where:
        [language, input] << [
            ["English", """
ELF Header:
 Magic:   7f 45 4c 46 01 01 01 00 00 00 00 00 00 00 00 00
 Class:                             ELF32
 Data:                              2's complement, little endian
 Version:                           1 (current)
 OS/ABI:                            UNIX - System V
 ABI Version:                       0
 Type:                              EXEC (Executable file)
 Machine:                           Intel 80386
 Version:                           0x1
 Entry point address:               0x8048310
 Start of program headers:          52 (bytes into file)
 Start of section headers:          4400 (bytes into file)
 Flags:                             0x0
 Size of this header:               52 (bytes)
 Size of program headers:           32 (bytes)
 Number of program headers:         8
 Size of section headers:           40 (bytes)
 Number of section headers:         29
 Section header string table index: 26
"""],
            ["German", """
ELF-Header:
  Magic:   7f 45 4c 46 01 01 01 00 00 00 00 00 00 00 00 00
  Klasse:                            ELF32
  Daten:                             2er-Komplement, Little-Endian
  Version:                           1 (current)
  OS/ABI:                            UNIX - System V
  ABI-Version:                       0
  Typ:                               DYN (geteilte Objektadatei)
  Maschine:                          Intel 80386
  Version:                           0x1
  Einstiegspunktadresse:               0x19bc0
  Beginn der Programm-Header:          52 (Bytes in Datei)
  Beginn der Sektions-header:          1739908 (Bytes in Datei)
  Flags:                             0x0
  Größe dieses Headers:              52 (Byte)
  Größe der Programm-Header:         32 (Byte)
  Number of program headers:         10
  Größe der Sektions-Header:         40 (bytes)
  Anzahl der Sektions-Header:        67
  Sektions-Header Stringtabellen-Index: 66
"""
            ]]
    }

    @Unroll("reads architecture value and throws RuntimeException with #language readelf output")
    def "reads architecture value and throws RuntimeException"() {
        when:
        def inputLines = input.readLines()
        ReadelfBinaryInfo.readArch(inputLines)

        then:
        thrown(RuntimeException)

        where:
        [language, input] << [
            ["English", """
ELF Header:
 Magic:   7f 45 4c 46 01 01 01 00 00 00 00 00 00 00 00 00
 Class:                             ELF32
 Data:                              2's complement, little endian
 Version:                           1 (current)
 OS/ABI:                            UNIX - System V
 ABI Version:                       0
 Type:                              EXEC (Executable file)
 Version:                           0x1
 Entry point address:               0x8048310
 Start of program headers:          52 (bytes into file)
 Start of section headers:          4400 (bytes into file)
 Flags:                             0x0
 Size of this header:               52 (bytes)
 Size of program headers:           32 (bytes)
 Number of program headers:         8
 Size of section headers:           40 (bytes)
 Number of section headers:         29
 Section header string table index: 26
"""],
            ["German", """
ELF-Header:
  Magic:   7f 45 4c 46 01 01 01 00 00 00 00 00 00 00 00 00
  Klasse:                            ELF32
  Daten:                             2er-Komplement, Little-Endian
  Version:                           1 (current)
  OS/ABI:                            UNIX - System V
  ABI-Version:                       0
  Typ:                               DYN (geteilte Objektadatei)
  Version:                           0x1
  Einstiegspunktadresse:               0x19bc0
  Beginn der Programm-Header:          52 (Bytes in Datei)
  Beginn der Sektions-header:          1739908 (Bytes in Datei)
  Flags:                             0x0
  Größe dieses Headers:              52 (Byte)
  Größe der Programm-Header:         32 (Byte)
  Number of program headers:         10
  Größe der Sektions-Header:         40 (bytes)
  Anzahl der Sektions-Header:        67
  Sektions-Header Stringtabellen-Index: 66
"""
            ]]
    }

}
