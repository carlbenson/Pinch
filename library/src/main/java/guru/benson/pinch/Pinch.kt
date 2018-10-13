/*
 * Copyright 2013 Carl Benson
 * Copyright 2014-2015 Mattias Niiranen
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

import java.io.BufferedInputStream
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import java.util.zip.CRC32
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import java.util.zip.ZipEntry

/**
 * Class allows for single file downloads from a ZIP file stored on an HTTP server.
 * @param url       The URL pointing to the ZIP file on the HTTP server.
 * @param userAgent User-agent to be used together with the download request.
 */
class Pinch
@JvmOverloads constructor(private var url: URL, private val userAgent: String? = null) {

    private var centralDirectorySize: Int = 0

    private var centralDirectoryOffset: Int = 0

    /**
     * Read the content length for the ZIP file.
     *
     * @return The content length in bytes or -1 failed.
     */
    private fun httpFileSize(): Int {
        var conn: HttpURLConnection? = null
        var length = -1
        try {
            conn = openConnection()
            conn.requestMethod = "HEAD"
            conn.connect()
            if (conn.responseCode == HttpURLConnection.HTTP_MOVED_TEMP) {
                if (setUrl(conn.getHeaderField("Location"))) {
                    disconnect(conn)
                    length = httpFileSize()
                }
            } else {
                length = conn.contentLength
            }
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            disconnect(conn)
        }

        log("Content length is $length bytes")

        return length
    }

    private fun setUrl(urlString: String): Boolean {
        try {
            url = URL(urlString)
        } catch (e: MalformedURLException) {
            return false
        }

        return true
    }

    @Throws(IOException::class)
    private fun openConnection(): HttpURLConnection {
        val conn = url.openConnection() as HttpURLConnection
        if (userAgent != null) {
            conn.setRequestProperty("User-agent", userAgent)
        }
        return conn
    }

    /**
     * Searches for the ZIP central directory.
     *
     * @param length The content length of the file to search.
     * @return `true` if central directory was found and parsed, otherwise `false`
     */
    private fun findCentralDirectory(length: Int): Boolean {
        var conn: HttpURLConnection? = null
        var bis: InputStream? = null

        val start = (length - 4096).toLong()
        val end = (length - 1).toLong()
        val data: ByteArray

        try {
            conn = openConnection()
            conn.setRequestProperty("Range", "bytes=$start-$end")
            conn.instanceFollowRedirects = true
            conn.connect()

            val responseCode = conn.responseCode
            if (responseCode != HttpURLConnection.HTTP_PARTIAL) {
                throw IOException("Unexpected HTTP server response: $responseCode")
            }

            bis = conn.inputStream
            data = bis.readBytes()

            log("Read ${data.size} bytes")

        } catch (e: IOException) {
            e.printStackTrace()
            return false
        } finally {
            close(bis)
            disconnect(conn)
        }

        return parseEndOfCentralDirectory(data)
    }

    /**
     * Parses the ZIP central directory from a byte buffer.
     *
     * @param data The byte buffer to be parsed.
     * @return `true` if central directory was parsed, otherwise `false`
     */
    private fun parseEndOfCentralDirectory(data: ByteArray): Boolean {

        val zipEndSignature = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(ZipConstants.ENDSIG.toInt()).array()

        // find start of ZIP archive signature.
        val index = indexOf(data, zipEndSignature)

        if (index < 0) {
            return false
        }

        // wrap head around a part of the buffer starting with index.
        val buf = ByteBuffer.wrap(data, index, data.size - index)
        buf.order(ByteOrder.LITTLE_ENDIAN)

        // For ZIP header descriptions, see
        // http://en.wikipedia.org/wiki/ZIP_(file_format)#File_headers

        // skip signature
        buf.int
        // skip numberOfThisDisk
        buf.short
        // skip diskWhereCentralDirectoryStarts =
        buf.short
        // skip numberOfCentralDirectoriesOnThisDisk
        buf.short

        // skip entriesCount
        buf.short

        centralDirectorySize = buf.int

        centralDirectoryOffset = buf.int

        // skip zipFileCommentLength
        buf.short

        return true
    }

    /**
     * Gets the list of files included in the ZIP stored on the HTTP server.
     *
     * @return A list of ZIP entries.
     */
    fun parseCentralDirectory(): ArrayList<ExtendedZipEntry>? {
        val contentLength = httpFileSize()
        if (contentLength <= 0) {
            return null
        }

        if (!findCentralDirectory(contentLength)) {
            return null
        }

        var conn: HttpURLConnection? = null
        var bis: InputStream? = null

        val start = centralDirectoryOffset.toLong()
        val end = start + centralDirectorySize - 1

        val data: ByteArray
        val buf = ByteBuffer.allocate(centralDirectorySize)

        try {
            conn = openConnection()
            conn.setRequestProperty("Range", "bytes=$start-$end")
            conn.instanceFollowRedirects = true
            conn.connect()

            val responseCode = conn.responseCode
            if (responseCode != HttpURLConnection.HTTP_PARTIAL) {
                throw IOException("Unexpected HTTP server response: $responseCode")
            }

            bis = conn.inputStream
            data = bis.readBytes()

            log("Central directory is ${data.size} bytes")

        } catch (e: IOException) {
            e.printStackTrace()
            return null
        } finally {
            close(bis)
            disconnect(conn)
        }

        return parseHeaders(buf)
    }

    /**
     * Wrapper method for [.downloadFile] where `name` is
     * extracted from `entry`.
     */
    @Throws(IOException::class, InterruptedException::class)
    fun downloadFile(entry: ExtendedZipEntry) {
        downloadFile(entry, null, entry.name, null)
    }

    @Throws(IOException::class, InterruptedException::class)
    fun downloadFile(entry: ExtendedZipEntry, dir: String) {
        downloadFile(entry, dir, entry.name, null)
    }

    /**
     * Wrapper method for [.downloadFile] where `name` is extracted from `entry`.
     */
    @Throws(IOException::class, InterruptedException::class)
    fun downloadFile(entry: ExtendedZipEntry, dir: String, listener: ProgressListener) {
        downloadFile(entry, dir, entry.name, listener)
    }

    /**
     * Download and inflate file from a ZIP stored on a HTTP server.
     *
     * @param entry Entry representing file to download.
     * @param name  Path where to store the downloaded file.
     * @throws IOException          If an error occurred while reading from network or writing to
     * disk.
     * @throws InterruptedException If the thread was interrupted.
     */
    @Throws(IOException::class, InterruptedException::class)
    fun downloadFile(
        entry: ExtendedZipEntry, dir: String?, name: String,
        listener: ProgressListener?
    ) {
        var conn: HttpURLConnection? = null
        var inputStream: InputStream? = null
        var fos: FileOutputStream? = null

        try {
            val outFile = File(if (dir != null) dir + File.separator + name else name)

            if (!outFile.exists()) {
                if (outFile.parentFile != null) {
                    outFile.parentFile.mkdirs()
                }
            }

            // no need to download 0 byte size directories
            if (entry.isDirectory) {
                return
            }

            fos = FileOutputStream(outFile)
            conn = getEntryInputStream(entry)

            // this is a stored (non-deflated) file, read it raw without inflating it
            inputStream = if (entry.method == ZipEntry.STORED) {
                BufferedInputStream(conn.inputStream)
            } else {
                InflaterInputStream(conn.inputStream, Inflater(true))
            }

            var read = 0
            var bytes = 0
            val totalSize = entry.size.toInt()
            val buf = ByteArray(2048)
            read = inputStream.read(buf)
            while (read != -1) {
                read = inputStream.read(buf)
                if (Thread.currentThread().isInterrupted) {
                    throw InterruptedException("Download was interrupted")
                }
                // Ignore any extra data
                if (totalSize < read + bytes) {
                    read = totalSize - bytes
                }

                fos.write(buf, 0, read)
                bytes += read
                listener?.onProgress(bytes.toLong(), read.toLong(), entry.size)
            }

            log("Wrote $bytes bytes to $name")
        } finally {
            close(fos)
            close(inputStream)
            disconnect(conn)
        }
    }

    /**
     * Get a [java.net.HttpURLConnection] that has its [java.io.InputStream] pointing at
     * the file data of the given [guru.benson.pinch.ExtendedZipEntry].
     */
    @Throws(IOException::class)
    private fun getEntryInputStream(entry: ExtendedZipEntry): HttpURLConnection {
        var conn: HttpURLConnection = openConnection()

        // Define the local header range
        var start = entry.offset
        var end = start + ZipConstants.LOCHDR

        conn.setRequestProperty("Range", "bytes=$start-$end")
        conn.instanceFollowRedirects = true
        conn.connect()

        var responseCode = conn.responseCode
        if (responseCode != HttpURLConnection.HTTP_PARTIAL) {
            throw IOException("Unexpected HTTP server response: $responseCode")
        }

        val dataBuffer = conn.inputStream.readBytes()
        disconnect(conn)
        if (dataBuffer.size < ZipConstants.LOCHDR) {
            throw IOException("Unable to fetch the local header")
        }

        val buffer = ByteBuffer.allocate(ZipConstants.LOCHDR)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(dataBuffer, 0, ZipConstants.LOCHDR)

        val headerSignature = buffer.getInt(0)
        if (headerSignature.toLong() != ZipConstants.LOCSIG) {
            disconnect(conn)
            throw IOException("Local file header signature mismatch")
        }

        var localCompressedSize = buffer.getInt(ZipConstants.LOCSIZ)
        val localFileNameLength = buffer.getShort(ZipConstants.LOCNAM)
        val localExtraLength = buffer.getShort(ZipConstants.LOCEXT)

        // Mac OSX sets the local size to zero when creating the zip in Finder.
        if (localCompressedSize == 0) {
            localCompressedSize = entry.compressedSize.toInt()
        }

        // Define the local file range
        start = entry.offset + ZipConstants.LOCHDR + localFileNameLength + localExtraLength
        end = start + localCompressedSize

        // Open a new one with
        conn = openConnection()
        conn.setRequestProperty("Range", "bytes=$start-$end")
        conn.instanceFollowRedirects = true
        conn.connect()

        responseCode = conn.responseCode
        if (responseCode != HttpURLConnection.HTTP_PARTIAL) {
            disconnect(conn)
            throw IOException("Unexpected HTTP server response: $responseCode")
        }

        return conn
    }

    /**
     * Extract all ZipEntries from the ZIP central directory.
     *
     * @param buf The byte buffer containing the ZIP central directory.
     * @return A list with all ZipEntries.
     */
    private fun parseHeaders(buf: ByteBuffer): ArrayList<ExtendedZipEntry> {
        val zeList = ArrayList<ExtendedZipEntry>()

        buf.order(ByteOrder.LITTLE_ENDIAN)

        var offset = 0

        while (offset < buf.limit() - ZipConstants.CENHDR) {
            val fileNameLen = buf.getShort(offset + ZipConstants.CENNAM)
            val extraFieldLen = buf.getShort(offset + ZipConstants.CENEXT)
            val fileCommentLen = buf.getShort(offset + ZipConstants.CENCOM)

            val fileName =
                String(buf.array(), offset + ZipConstants.CENHDR, fileNameLen.toInt())

            val zeGermans = ExtendedZipEntry(fileName)

            zeGermans.method = buf.getShort(offset + ZipConstants.CENHOW).toInt()

            val crc = CRC32()
            crc.update(buf.getInt(offset + ZipConstants.CENCRC))
            zeGermans.crc = crc.value
            zeGermans.compressedSize = buf.getInt(offset + ZipConstants.CENSIZ).toLong()
            zeGermans.size = buf.getInt(offset + ZipConstants.CENLEN).toLong()
            zeGermans.internalAttr = buf.getShort(offset + ZipConstants.CENATT)
            zeGermans.externalAttr = buf.getShort(offset + ZipConstants.CENATX)
            zeGermans.offset = buf.getInt(offset + ZipConstants.CENOFF).toLong()

            zeGermans.extraLength = extraFieldLen

            zeList.add(zeGermans)
            offset += ZipConstants.CENHDR + fileNameLen.toInt() + extraFieldLen.toInt() + fileCommentLen.toInt()
        }

        return zeList
    }

    interface ProgressListener {

        /**
         * @param progressTotal Number of bytes that have been downloaded.
         * @param progressDelta Number of bytes that have been downloaded since last update.
         * @param totalSize     Total size in bytes.
         */
        fun onProgress(progressTotal: Long, progressDelta: Long, totalSize: Long)
    }


    private val LOG_TAG = Pinch::class.java.simpleName

    /**
     * Handy log method.
     *
     * @param msg The message to print to debug log.
     */
    private fun log(msg: String) {
        if (BuildConfig.DEBUG) {
            android.util.Log.d(LOG_TAG, msg)
        }
    }

    /**
     * Handy close method to avoid nestled try/catch blocks.
     *
     * @param c The object to close.
     */
    private fun close(c: Closeable?) {
        if (c != null) {
            try {
                c.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }

        }
    }

    /**
     * Handy disconnect method to wrap null-check.
     *
     * @param c Connection to disconnect.
     */
    private fun disconnect(c: HttpURLConnection?) {
        c?.disconnect()
    }
}
