/*
 * Copyright 2013 Carl Benson
 * Copyright 2014 Mattias Niiranen
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

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.zip.CRC32;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipEntry;

import org.apache.http.client.methods.HttpHead;

/**
 * This class allows for single file downloads from a ZIP file stored on an HTTP server.
 */
public class Pinch {

    final public static String LOG_TAG = Pinch.class.getSimpleName();

    public interface ProgressListener {
        /**
         * @param progressTotal Number of bytes that have been downloaded.
         * @param progressDelta Number of bytes that have been downloaded since last update.
         * @param totalSize Total size in bytes.
         */
        public void onProgress(long progressTotal, long progressDelta, long totalSize);
    }

    private URL mUrl;
    private String mUserAgent;

    private short entriesCount;
    private short zipFileCommentLength;

    private int centralDirectorySize;
    private int centralDirectoryOffset;

    /**
     * Class constructor.
     *
     * @param url
     *            The URL pointing to the ZIP file on the HTTP server.
     */
    public Pinch(URL url) {
        this(url, null);
    }

    /**
     * Class constructor.
     * @param url The URL pointing to the ZIP file on the HTTP server.
     * @param userAgent User-agent to be used together with the download request.
     */
    public Pinch(URL url, String userAgent) {
        mUrl = url;
        mUserAgent = userAgent;
    }

    private boolean setUrl(String url) {
        try {
            mUrl = new URL(url);
        } catch (MalformedURLException e) {
            return false;
        }
        return true;
    }

    /**
     * Handy log method.
     *
     * @param msg
     *            The message to print to debug log.
     */
    private static void log(String msg) {
        if (BuildConfig.DEBUG) {
            android.util.Log.d(LOG_TAG, msg);
        }
    }

    /**
     * Handy close method to avoid nestled try/catch blocks.
     *
     * @param c
     *            The object to close.
     */
    private static void close(Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private HttpURLConnection openConnection() throws IOException {
        HttpURLConnection conn = (HttpURLConnection) mUrl.openConnection();
        if (mUserAgent != null) {
            conn.setRequestProperty("User-agent", mUserAgent);
        }
        return conn;
    }

    /**
     * Handy disconnect method to wrap null-check.
     *
     * @param c
     *            Connection to disconnect.
     */
    private static void disconnect(HttpURLConnection c) {
        if (c != null) {
            c.disconnect();
        }
    }

    /**
     * Read the content length for the ZIP file.
     *
     * @return The content length in bytes or -1 failed.
     */
    private int getHttpFileSize() {
        HttpURLConnection conn = null;
        int length = -1;
        try {
            conn = openConnection();
            conn.setRequestMethod(HttpHead.METHOD_NAME);
            conn.connect();

            // handle re-directs
            if (conn.getResponseCode() == HttpURLConnection.HTTP_MOVED_TEMP) {
                if (setUrl(conn.getHeaderField("Location"))) {
                    disconnect(conn);
                    length = getHttpFileSize();
                }
            } else {
                length = conn.getContentLength();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            disconnect(conn);
        }

        log("Content length is " + length + " bytes");

        return length;
    }

    /**
     * Searches for the ZIP central directory.
     *
     * @param length
     *            The content length of the file to search.
     * @return {@code true} if central directory was found and parsed, otherwise {@code false}
     */
    private boolean findCentralDirectory(int length) {
        HttpURLConnection conn = null;
        InputStream bis = null;

        long start = length - 4096;
        long end = length - 1;
        byte[] data = new byte[2048];

        try {
            conn = openConnection();
            conn.setRequestProperty("Range", "bytes=" + start + "-" + end);
            conn.setInstanceFollowRedirects(true);
            conn.connect();

            int responseCode = conn.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_PARTIAL) {
                throw new IOException("Unexpected HTTP server response: " + responseCode);
            }

            bis = conn.getInputStream();
            int read, bytes = 0;
            while ((read = bis.read(data)) != -1) {
                bytes += read;
            }

            log("Read " + bytes + " bytes");

        } catch (IOException e) {
            e.printStackTrace();
            return false;
        } finally {
            close(bis);
            disconnect(conn);
        }

        return parseEndOfCentralDirectory(data);
    }

    /**
     * Parses the ZIP central directory from a byte buffer.
     *
     * @param data
     *            The byte buffer to be parsed.
     * @return {@code true} if central directory was parsed, otherwise {@code false}
     */
    private boolean parseEndOfCentralDirectory(byte[] data) {

        byte[] zipEndSignature = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt((int) ZipConstants.ENDSIG).array();

        // find start of ZIP archive signature.
        int index = KMPMatch.indexOf(data, zipEndSignature);

        if (index < 0) {
            return false;
        }

        // wrap our head around a part of the buffer starting with index.
        ByteBuffer buf = ByteBuffer.wrap(data, index, data.length - index);
        buf.order(ByteOrder.LITTLE_ENDIAN);

        /*
         * For ZIP header descriptions, see
         * http://en.wikipedia.org/wiki/ZIP_(file_format)#File_headers
         */

        // skip signature
        buf.getInt();
        // skip numberOfThisDisk
        buf.getShort();
        // skip diskWhereCentralDirectoryStarts =
        buf.getShort();
        // skip numberOfCentralDirectoriesOnThisDisk
        buf.getShort();

        entriesCount = buf.getShort();
        centralDirectorySize = buf.getInt();
        centralDirectoryOffset = buf.getInt();
        zipFileCommentLength = buf.getShort();

        return true;
    }

    /**
     * Extract all ZipEntries from the ZIP central directory.
     *
     * @param buf
     *            The byte buffer containing the ZIP central directory.
     * @return A list with all ZipEntries.
     */
    private static ArrayList<ExtendedZipEntry> parseHeaders(ByteBuffer buf) {
        ArrayList<ExtendedZipEntry> zeList = new ArrayList<ExtendedZipEntry>();

        buf.order(ByteOrder.LITTLE_ENDIAN);

        int offset = 0;

        while (offset < buf.limit() - ZipConstants.CENHDR) {
            short fileNameLen = buf.getShort(offset + ZipConstants.CENNAM);
            short extraFieldLen = buf.getShort(offset + ZipConstants.CENEXT);
            short fileCommentLen = buf.getShort(offset + ZipConstants.CENCOM);

            String fileName = new String(buf.array(), offset + ZipConstants.CENHDR, fileNameLen);

            ExtendedZipEntry zeGermans = new ExtendedZipEntry(fileName);

            zeGermans.setMethod(buf.getShort(offset + ZipConstants.CENHOW));

            CRC32 crc = new CRC32();
            crc.update(buf.getInt(offset + ZipConstants.CENCRC));
            zeGermans.setCrc(crc.getValue());

            zeGermans.setCompressedSize(buf.getInt(offset + ZipConstants.CENSIZ));
            zeGermans.setSize(buf.getInt(offset + ZipConstants.CENLEN));
            zeGermans.setInternalAttr(buf.getShort(offset + ZipConstants.CENATT));
            zeGermans.setExternalAttr(buf.getShort(offset + ZipConstants.CENATX));
            zeGermans.setOffset((long) buf.getInt(offset + ZipConstants.CENOFF));

            zeGermans.setExtraLength(extraFieldLen);

            zeList.add(zeGermans);
            offset += ZipConstants.CENHDR + fileNameLen + extraFieldLen + fileCommentLen;
        }

        return zeList;
    }

    /**
     * Gets the list of files included in the ZIP stored on the HTTP server.
     *
     * @return A list of ZIP entries.
     */
    public ArrayList<ExtendedZipEntry> parseCentralDirectory() {
        int contentLength = getHttpFileSize();
        if (contentLength <= 0) {
            return null;
        }

        if (!findCentralDirectory(contentLength)) {
            return null;
        }

        HttpURLConnection conn = null;
        InputStream bis = null;

        long start = centralDirectoryOffset;
        long end = start + centralDirectorySize - 1;

        byte[] data = new byte[2048];
        ByteBuffer buf = ByteBuffer.allocate(centralDirectorySize);

        try {
            conn = openConnection();
            conn.setRequestProperty("Range", "bytes=" + start + "-" + end);
            conn.setInstanceFollowRedirects(true);
            conn.connect();

            int responseCode = conn.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_PARTIAL) {
                throw new IOException("Unexpected HTTP server response: " + responseCode);
            }

            bis = conn.getInputStream();
            int read, bytes = 0;
            while ((read = bis.read(data)) != -1) {
                buf.put(data, 0, read);
                bytes += read;
            }

            log("Central directory is " + bytes + " bytes");

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } finally {
            close(bis);
            disconnect(conn);
        }

        return parseHeaders(buf);
    }

    /**
     * Wrapper method for {@link #downloadFile(ExtendedZipEntry, String)} where {@code name} is extracted from {@code entry}.
     */
    public void downloadFile(ExtendedZipEntry entry) throws IOException, InterruptedException {
        downloadFile(entry, null, entry.getName(), null);
    }

    public void downloadFile(ExtendedZipEntry entry, String dir) throws IOException, InterruptedException {
        downloadFile(entry, dir, entry.getName(), null);
    }

    /**
     * Wrapper method for {@link #downloadFile(ExtendedZipEntry, String, String, guru.benson.pinch.Pinch.ProgressListener)} 
     * where {@code name} is extracted from {@code entry}.
     */
    public void downloadFile(ExtendedZipEntry entry, String dir, ProgressListener listener) throws IOException, InterruptedException {
        downloadFile(entry, dir, entry.getName(), listener);
    }

    /**
     * Download and inflate file from a ZIP stored on a HTTP server.
     *
     * @param entry
     *            Entry representing file to download.
     * @param name
     *            Path where to store the downloaded file.
     * @param listener
     *
     * @throws IOException
     *             If an error occurred while reading from network or writing to disk.
     * @throws InterruptedException
     *             If the thread was interrupted.
     */
    public void downloadFile(ExtendedZipEntry entry, String dir, String name, ProgressListener listener) throws IOException, InterruptedException {
        HttpURLConnection conn = null;
        InputStream is = null;
        FileOutputStream fos = null;

        // this is where the local file header starts
        long start = entry.getOffset()
                + ZipConstants.LOCHDR
                + entry.getName().length()
                + entry.getExtraLength();

        long end = start + entry.getCompressedSize();
        try {
            File outFile = new File(dir != null ? dir + File.separator + name : name);

            if (!outFile.exists()) {
                if (outFile.getParentFile() != null) {
                    outFile.getParentFile().mkdirs();
                }
            }

            // no need to download 0 byte size directories
            if (entry.isDirectory()) {
                return;
            }

            fos = new FileOutputStream(outFile);

            conn = openConnection();
            conn.setRequestProperty("Range", "bytes=" + start + "-" + end);
            conn.setInstanceFollowRedirects(true);
            conn.connect();

            int responseCode = conn.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_PARTIAL) {
                throw new IOException("Unexpected HTTP server response: " + responseCode);
            }

            byte[] buf = new byte[2048];
            int read, bytes = 0;

            // this is a stored (non-deflated) file, read it raw without inflating it
            if (entry.getMethod() == ZipEntry.STORED) {
                is = new BufferedInputStream(conn.getInputStream());
            } else {
                is = new InflaterInputStream(conn.getInputStream(), new Inflater(true));
            }

            long totalSize = entry.getSize();
            while ((read = is.read(buf)) != -1) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException("Download was interrupted");
                }
                fos.write(buf, 0, read);
                bytes += read;
                if (listener != null) {
                    listener.onProgress(bytes, read, totalSize);
                }
            }

            log("Wrote " + bytes + " bytes to " + name);
        } finally {
            close(fos);
            close(is);
            disconnect(conn);
        }
    }
}

