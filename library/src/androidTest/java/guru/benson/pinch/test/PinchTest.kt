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

package guru.benson.pinch.test

import android.os.Environment
import android.support.test.runner.AndroidJUnit4
import guru.benson.pinch.Pinch
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.URL

@RunWith(AndroidJUnit4::class)
class PinchTest {

    private val DOWNLOAD_DIR = (
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toString()
                    + File.separator + "PinchTest")

    @Test
    fun fetchDirectory() {
        val url = URL("http://www.cbconsulting.se/files/BSHInform.10.1.1-1.mib")
        val p = Pinch(url)
        val list = p.parseCentralDirectory()
        list?.forEach {
            p.downloadFile(it, DOWNLOAD_DIR + File.separator + "testFetchDirectory")
        }
    }

    @Test
    fun fetchZipWithCentralAndLocalExtraMismatch() {
        val url = URL("http://niiranen.net/assets/demo_1280_800_149.zip")
        val pinch = Pinch(url)
        val contents = pinch.parseCentralDirectory()
        contents?.forEach {
            pinch.downloadFile(
                it,
                DOWNLOAD_DIR + File.separator + "testFetchZipWithInvalidCentralData"
            )
        }
    }

    @Test
    fun fetchZipWithZeroCompressedSizeLocalHeaders() {
        val url = URL("http://niiranen.net/assets/local-header-compressed-size-0.zip")
        val pinch = Pinch(url)
        val contents = pinch.parseCentralDirectory()
        contents?.forEach {
            pinch.downloadFile(
                it, DOWNLOAD_DIR + File.separator
                        + "testFetchZipWithZeroCompressedSizeLocalHeaders"
            )
        }
    }
}
