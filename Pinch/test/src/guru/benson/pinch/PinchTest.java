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
package guru.benson.pinch;

import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.MockWebServer;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

import okio.Buffer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PinchTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private MockWebServer mockWebServer;

    @Before
    public void setUp() throws Exception {
        mockWebServer = new MockWebServer();
    }

    @Test
    public void testParseCentralDirectoryServerError() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_INTERNAL_ERROR));
        Pinch pinch = new Pinch(mockWebServer.url("/").url());
        assertNull(pinch.parseCentralDirectory());
    }

    @Test
    public void testParseCentralDirectory() throws Exception {
        URL testZip = getClass().getClassLoader().getResource("test.zip");
        assertNotNull(testZip);
        enqueueForCentralHeaderParsing(testZip);
        Pinch pinch = new Pinch(mockWebServer.url("/").url());
        ArrayList<ExtendedZipEntry> entries = pinch.parseCentralDirectory();
        assertNotNull(entries);

        testZip = getClass().getClassLoader().getResource("testSmall.zip");
        assertNotNull(testZip);
        enqueueForCentralHeaderParsing(testZip);
        pinch = new Pinch(mockWebServer.url("/").url());
        entries = pinch.parseCentralDirectory();
        assertNotNull(entries);
    }

    @Test
    public void testDownloadFile() throws Exception {
        URL testZip = getClass().getClassLoader().getResource("test.zip");
        enqueueForCentralHeaderParsing(testZip);

        Pinch pinch = new Pinch(mockWebServer.url("/").url());
        ArrayList<ExtendedZipEntry> entries = pinch.parseCentralDirectory();
        assertNotNull(entries);
        assertTrue(entries.size() > 0);
        ExtendedZipEntry zipEntry = entries.get(0);
        enqueueForFileDownload(testZip, zipEntry);
        File downloadDir = temporaryFolder.newFolder();
        pinch.downloadFile(zipEntry, downloadDir.getPath(), "test", null);
        File pinched = new File(downloadDir, "test");
        assertTrue(pinched.exists());
        assertEquals(zipEntry.getSize(), pinched.length());

        testZip = getClass().getClassLoader().getResource("testSmall.zip");
        enqueueForCentralHeaderParsing(testZip);

        pinch = new Pinch(mockWebServer.url("/").url());
        entries = pinch.parseCentralDirectory();
        assertNotNull(entries);
        assertTrue(entries.size() > 0);
        zipEntry = entries.get(0);
        enqueueForFileDownload(testZip, zipEntry);
        downloadDir = temporaryFolder.newFolder();
        pinch.downloadFile(zipEntry, downloadDir.getPath(), "testSmall", null);
        pinched = new File(downloadDir, "testSmall");
        assertTrue(pinched.exists());
        assertEquals(zipEntry.getSize(), pinched.length());
    }

    void enqueueForCentralHeaderParsing(URL zip) throws IOException {
        int zipLength = zip.openConnection().getContentLength();
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Length", zipLength));
        byte[] centralHeader = getCentralDirectoryHeader(zip.openStream(), zipLength);
        mockWebServer.enqueue(new MockResponse()
                .setBody(new Buffer()
                        .readFrom(new ByteArrayInputStream(centralHeader)))
                .setResponseCode(HttpURLConnection.HTTP_PARTIAL));


        byte[] zipEndSignature = ByteBuffer.allocate(4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt((int) ZipConstants.ENDSIG)
                .array();
        // find start of ZIP archive signature.
        int index = KMPMatch.indexOf(centralHeader, zipEndSignature);
        assertTrue(index > 0);
        ByteBuffer buf = ByteBuffer.wrap(centralHeader, index, centralHeader.length - index);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        // skip signature
        buf.getInt();
        // skip numberOfThisDisk
        buf.getShort();
        // skip diskWhereCentralDirectoryStarts =
        buf.getShort();
        // skip numberOfCentralDirectoriesOnThisDisk
        buf.getShort();

        short entriesCount = buf.getShort();
        int centralDirectorySize = buf.getInt();
        int centralDirectoryOffset = buf.getInt();
        short zipFileCommentLength = buf.getShort();

        mockWebServer.enqueue(new MockResponse()
                .setBody(new Buffer()
                        .readFrom(new ByteArrayInputStream(
                                getCentralDirectory(zip.openStream(),
                                        centralDirectoryOffset,
                                        centralDirectorySize - 1))))
                .setResponseCode(HttpURLConnection.HTTP_PARTIAL));
    }

    void enqueueForFileDownload(URL zip, ExtendedZipEntry zipEntry) throws IOException {
        mockWebServer.enqueue(new MockResponse()
                .setBody(new Buffer()
                        .readFrom(new ByteArrayInputStream(
                                getLocalHeader(zip.openStream(), zipEntry))))
                .setResponseCode(HttpURLConnection.HTTP_PARTIAL));
        mockWebServer.enqueue(new MockResponse()
                .setBody(new Buffer()
                        .readFrom(new ByteArrayInputStream(
                                getLocalFile(zip.openStream(), zipEntry))))
                .setResponseCode(HttpURLConnection.HTTP_PARTIAL));
    }

    byte[] getCentralDirectoryHeader(InputStream inputStream, int length) throws IOException {
        assertTrue(inputStream.skip(Math.max(length - 4096, 0)) >= 0);
        byte[] data = new byte[2048];
        int read, bytes = 0;
        while ((read = inputStream.read(data)) != -1) {
            bytes += read;
        }
        return data;
    }

    byte[] getCentralDirectory(InputStream inputStream, int offset, int length) throws IOException {
        assertTrue(inputStream.skip(offset) >= 0);
        byte[] data = new byte[2048];
        ByteBuffer buf = ByteBuffer.allocate(length);
        int read, bytes = 0;
        while ((read = inputStream.read(data)) != -1) {
            buf.put(data, bytes, Math.min(length, read));
            bytes += read;
            if (bytes >= length) {
                break;
            }
        }
        return buf.array();
    }

    byte[] getLocalHeader(InputStream inputStream, ExtendedZipEntry zipEntry) throws IOException {
        long offset = zipEntry.getOffset();
        int len = ZipConstants.LOCHDR;
        byte[] data = new byte[ZipConstants.LOCHDR];
        //noinspection ResultOfMethodCallIgnored
        inputStream.skip(offset);
        int read = inputStream.read(data, 0, len);
        assertEquals(ZipConstants.LOCHDR, read);
        return data;
    }

    byte[] getLocalFile(InputStream inputStream, ExtendedZipEntry zipEntry) throws IOException {
        long offset = zipEntry.getOffset() + ZipConstants.LOCHDR +
                zipEntry.getName().length() + zipEntry.getExtraLength();
        int length = (int) zipEntry.getCompressedSize();
        byte[] data = new byte[2048];
        //noinspection ResultOfMethodCallIgnored
        inputStream.skip(offset);
        ByteBuffer buf = ByteBuffer.allocate(length);
        int read, bytes = 0;
        while ((read = inputStream.read(data)) != -1) {
            buf.put(data, bytes, Math.min(length, read));
            bytes += read;
            if (bytes >= length) {
                break;
            }
        }
        return buf.array();
    }
}