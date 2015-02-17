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

package guru.benson.pinch;

import java.util.zip.ZipEntry;

/**
 * This class is an extension of {@link ZipEntry} to allow storage of more attributes.
 */
public class ExtendedZipEntry extends ZipEntry {

    private short mInternalAttr;
    private short mExternalAttr;
    private long  mOffset;
    private short mExtraLength;

    public ExtendedZipEntry(String name) {
        super(name);
    }

    public ExtendedZipEntry(ZipEntry ze) {
        super(ze);
    }

    public short getInternalAttr() {
        return mInternalAttr;
    }

    public short getExternalAttr() {
        return mExternalAttr;
    }

    public long getOffset() {
        return mOffset;
    }

    public short getExtraLength() {
        return mExtraLength;
    }

    public void setInternalAttr(short attr) {
        mInternalAttr = attr;
    }

    public void setExternalAttr(short attr) {
        mExternalAttr = attr;
    }

    public void setOffset(long offset) {
        mOffset = offset;
    }

    public void setExtraLength(short length) {
        mExtraLength = length;
    }
}
