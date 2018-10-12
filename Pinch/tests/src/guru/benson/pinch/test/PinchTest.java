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

package guru.benson.pinch.test;

import org.junit.Test;

import android.os.Environment;

import java.io.File;
import java.net.URL;
import java.util.List;

import guru.benson.pinch.ExtendedZipEntry;
import guru.benson.pinch.Pinch;

public class PinchTest {

    private final String DOWNLOAD_DIR =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    + File.separator + "PinchTest";

    @Test
    public void testFetchDirectory() throws Throwable {
        URL url = new URL("http://www.cbconsulting.se/files/BSHInform.10.1.1-1.mib");
        Pinch p = new Pinch(url);
        List<ExtendedZipEntry> list = p.parseCentralDirectory();
        for (ExtendedZipEntry entry : list) {
            p.downloadFile(entry, DOWNLOAD_DIR + File.separator + "testFetchDirectory");
        }
    }

    @Test
    public void testFetchZipWithCentralAndLocalExtraMismatch() throws Throwable {
        URL url = new URL("http://niiranen.net/assets/demo_1280_800_149.zip");
        Pinch pinch = new Pinch(url);
        List<ExtendedZipEntry> contents = pinch.parseCentralDirectory();
        for (ExtendedZipEntry entry : contents) {
            pinch.downloadFile(entry,
                    DOWNLOAD_DIR + File.separator + "testFetchZipWithInvalidCentralData");
        }
    }

    @Test
    public void testFetchZipWithZeroCompressedSizeLocalHeaders() throws Throwable {
        URL url = new URL("http://niiranen.net/assets/local-header-compressed-size-0.zip");
        Pinch pinch = new Pinch(url);
        List<ExtendedZipEntry> contents = pinch.parseCentralDirectory();
        for (ExtendedZipEntry entry : contents) {
            pinch.downloadFile(entry, DOWNLOAD_DIR + File.separator
                    + "testFetchZipWithZeroCompressedSizeLocalHeaders");
        }
    }
}
