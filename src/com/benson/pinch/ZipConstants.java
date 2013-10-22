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

package com.benson.pinch;

/**
 * Modified copy of java/util/zip/ZipConstants.java from Android source.
 * 
 * For ZIP header descriptions, see
 * http://en.wikipedia.org/wiki/ZIP_(file_format)#File_headers
 */
public class ZipConstants {
    /** signatures */
    public static final long LOCSIG = 0x4034b50;
    public static final long EXTSIG = 0x8074b50;
    public static final long CENSIG = 0x2014b50;
    public static final long ENDSIG = 0x6054b50;

    /** header sizes */
    public static final int LOCHDR = 30;
    public static final int EXTHDR = 16;
    public static final int CENHDR = 46;
    public static final int ENDHDR = 22;

    /** local header offsets */
    public static final int LOCVER = 4;
    public static final int LOCFLG = 6;
    public static final int LOCHOW = 8;
    public static final int LOCTIM = 10;
    public static final int LOCCRC = 14;
    public static final int LOCSIZ = 18;
    public static final int LOCLEN = 22;
    public static final int LOCNAM = 26;
    public static final int LOCEXT = 28;

    /** data descriptor offsets */
    public static final int EXTCRC = 4;
    public static final int EXTSIZ = 8;
    public static final int EXTLEN = 12;

    /** central directory offsets */
    public static final int CENVEM = 4;
    public static final int CENVER = 6;
    public static final int CENFLG = 8;
    public static final int CENHOW = 10;
    public static final int CENTIM = 12;
    public static final int CENCRC = 16;
    public static final int CENSIZ = 20;
    public static final int CENLEN = 24;
    public static final int CENNAM = 28;
    public static final int CENEXT = 30;
    public static final int CENCOM = 32;
    public static final int CENDSK = 34;
    public static final int CENATT = 36;
    public static final int CENATX = 38;
    public static final int CENOFF = 42;

    /** central directory end offsets */
    public static final int ENDSUB = 8;
    public static final int ENDTOT = 10;
    public static final int ENDSIZ = 12;
    public static final int ENDOFF = 16;
    public static final int ENDCOM = 20;
}
