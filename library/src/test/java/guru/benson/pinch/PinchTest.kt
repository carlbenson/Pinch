/*
 * Copyright 2015 Mattias Niiranen
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

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PinchTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val mockWebServer = MockWebServer()

    @Test
    fun testParseCentralDirectoryServerError() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_INTERNAL_ERROR)
        )
        val pinch = Pinch(mockWebServer.url("/").url())
        assertNull(pinch.parseCentralDirectory())
    }

    @Test
    @Throws(Exception::class)
    fun testParseCentralDirectory() {
        var testZip = javaClass.classLoader.getResource("test.zip")
        assertNotNull(testZip)
        enqueueForCentralHeaderParsing(testZip)
        var pinch = Pinch(mockWebServer.url("/").url())
        var entries = pinch.parseCentralDirectory()
        assertNotNull(entries)

        testZip = javaClass.classLoader.getResource("testSmall.zip")
        assertNotNull(testZip)
        enqueueForCentralHeaderParsing(testZip)
        pinch = Pinch(mockWebServer.url("/").url())
        entries = pinch.parseCentralDirectory()
        assertNotNull(entries)
    }

    @Test
    @Throws(Exception::class)
    fun testDownloadFile() {
        var testZip = javaClass.classLoader.getResource("test.zip")
        enqueueForCentralHeaderParsing(testZip)

        var pinch = Pinch(mockWebServer.url("/").url())
        var entries = pinch.parseCentralDirectory()
        assertNotNull(entries)
        assertTrue(entries!!.size > 0)
        var zipEntry = entries[0]
        enqueueForFileDownload(testZip, zipEntry)
        var downloadDir = temporaryFolder.newFolder()
        pinch.downloadFile(zipEntry, downloadDir.path, "test", null)
        var pinched = File(downloadDir, "test")
        assertTrue(pinched.exists())
        assertEquals(zipEntry.size, pinched.length())

        testZip = javaClass.classLoader.getResource("testSmall.zip")
        enqueueForCentralHeaderParsing(testZip)

        pinch = Pinch(mockWebServer.url("/").url())
        entries = pinch.parseCentralDirectory()
        assertNotNull(entries)
        assertTrue(entries!!.size > 0)
        zipEntry = entries[0]
        enqueueForFileDownload(testZip, zipEntry)
        downloadDir = temporaryFolder.newFolder()
        pinch.downloadFile(zipEntry, downloadDir.path, "testSmall", null)
        pinched = File(downloadDir, "testSmall")
        assertTrue(pinched.exists())
        assertEquals(zipEntry.size, pinched.length())
    }

    @Throws(IOException::class)
    private fun enqueueForCentralHeaderParsing(zip: URL) {
        val zipLength = zip.openConnection().contentLength
        mockWebServer.enqueue(
            MockResponse()
                .setHeader("Content-Length", zipLength)
        )
        val centralHeader = getCentralDirectoryHeader(zip.openStream(), zipLength)
        mockWebServer.enqueue(
            MockResponse()
                .setBody(
                    Buffer()
                        .readFrom(ByteArrayInputStream(centralHeader))
                )
                .setResponseCode(HttpURLConnection.HTTP_PARTIAL)
        )

        val zipEndSignature = ByteBuffer.allocate(4)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(ZipConstants.ENDSIG.toInt())
            .array()
        // find start of ZIP archive signature.
        val index = indexOf(centralHeader, zipEndSignature)
        assertTrue(index > 0)
        val buf = ByteBuffer.wrap(centralHeader, index, centralHeader.size - index)
        buf.order(ByteOrder.LITTLE_ENDIAN)
        // skip signature
        buf.int
        // skip numberOfThisDisk
        buf.short
        // skip diskWhereCentralDirectoryStarts =
        buf.short
        // skip numberOfCentralDirectoriesOnThisDisk
        buf.short

        val entriesCount = buf.short
        val centralDirectorySize = buf.int
        val centralDirectoryOffset = buf.int
        val zipFileCommentLength = buf.short

        mockWebServer.enqueue(
            MockResponse()
                .setBody(
                    Buffer()
                        .readFrom(
                            ByteArrayInputStream(
                                getCentralDirectory(
                                    zip.openStream(),
                                    centralDirectoryOffset,
                                    centralDirectorySize - 1
                                )
                            )
                        )
                )
                .setResponseCode(HttpURLConnection.HTTP_PARTIAL)
        )
    }

    @Throws(IOException::class)
    private fun enqueueForFileDownload(zip: URL, zipEntry: ExtendedZipEntry) {
        mockWebServer.enqueue(
            MockResponse()
                .setBody(
                    Buffer()
                        .readFrom(
                            ByteArrayInputStream(
                                getLocalHeader(zip.openStream(), zipEntry)
                            )
                        )
                )
                .setResponseCode(HttpURLConnection.HTTP_PARTIAL)
        )
        mockWebServer.enqueue(
            MockResponse()
                .setBody(
                    Buffer()
                        .readFrom(
                            ByteArrayInputStream(
                                getLocalFile(zip.openStream(), zipEntry)
                            )
                        )
                )
                .setResponseCode(HttpURLConnection.HTTP_PARTIAL)
        )
    }

    @Throws(IOException::class)
    private fun getCentralDirectoryHeader(inputStream: InputStream, length: Int): ByteArray {
        assertTrue(inputStream.skip(Math.max(length - 4096, 0).toLong()) >= 0)
        val data = ByteArray(2048)
        var read = 0
        var bytes = 0
        while (read != -1) {
            read = inputStream.read(data)
            bytes += read
        }
        return data
    }

    @Throws(IOException::class)
    private fun getCentralDirectory(inputStream: InputStream, offset: Int, length: Int): ByteArray {
        assertTrue(inputStream.skip(offset.toLong()) >= 0)
        val data = ByteArray(2048)
        val buf = ByteBuffer.allocate(length)
        var read = 0
        var bytes = 0
        while (read != -1) {
            read = inputStream.read(data)
            buf.put(data, bytes, Math.min(length, read))
            bytes += read
            if (bytes >= length) {
                break
            }
        }
        return buf.array()
    }

    @Throws(IOException::class)
    private fun getLocalHeader(inputStream: InputStream, zipEntry: ExtendedZipEntry): ByteArray {
        val offset = zipEntry.offset
        val len = ZipConstants.LOCHDR
        val data = ByteArray(ZipConstants.LOCHDR)

        inputStream.skip(offset)
        val read = inputStream.read(data, 0, len)
        assertEquals(ZipConstants.LOCHDR.toLong(), read.toLong())
        return data
    }

    @Throws(IOException::class)
    private fun getLocalFile(inputStream: InputStream, zipEntry: ExtendedZipEntry): ByteArray {
        val offset = zipEntry.offset + ZipConstants.LOCHDR +
                zipEntry.name.length + zipEntry.extraLength
        val length = zipEntry.compressedSize.toInt()
        val data = ByteArray(2048)

        inputStream.skip(offset)
        val buf = ByteBuffer.allocate(length)
        var read = 0
        var bytes = 0
        while (read != -1) {
            read = inputStream.read(data)
            buf.put(data, bytes, Math.min(length, read))
            bytes += read
            if (bytes >= length) {
                break
            }
        }
        return buf.array()
    }
}
