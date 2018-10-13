/*
 * Copyright 2013 Carl Benson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package guru.benson.pinch

/**
 * Modified copy of java/util/zip/ZipConstants.java from Android source.
 *
 * For ZIP header descriptions, see http://en.wikipedia.org/wiki/ZIP_(file_format)#File_headers
 */
object ZipConstants {

    // local header offsets
    const val LOCVER = 4

    const val EXTSIG: Long = 0x8074b50

    const val CENSIG: Long = 0x2014b50

    // data descriptor offsets
    const val EXTCRC = 4

    // central directory offsets
    const val CENVEM = 4

    const val EXTHDR = 16

    // central directory end offsets
    const val ENDSUB = 8

    const val ENDHDR = 22

    // signatures
    const val LOCSIG: Long = 0x4034b50

    const val LOCFLG = 6

    const val LOCHOW = 8

    const val LOCTIM = 10

    const val LOCCRC = 14

    const val ENDSIG: Long = 0x6054b50

    const val LOCLEN = 22

    // header sizes
    const val LOCHDR = 30

    const val CENHDR = 46

    const val LOCSIZ = 18

    const val EXTSIZ = 8

    const val EXTLEN = 12

    const val LOCNAM = 26

    const val CENVER = 6

    const val CENFLG = 8

    const val LOCEXT = 28

    const val CENTIM = 12

    const val CENHOW = 10

    const val CENCRC = 16

    const val CENSIZ = 20

    const val CENLEN = 24

    const val CENNAM = 28

    const val CENEXT = 30

    const val CENDSK = 34

    const val CENCOM = 32

    const val CENATT = 36

    const val CENATX = 38

    const val CENOFF = 42

    const val ENDTOT = 10

    const val ENDSIZ = 12

    const val ENDOFF = 16

    const val ENDCOM = 20
}
