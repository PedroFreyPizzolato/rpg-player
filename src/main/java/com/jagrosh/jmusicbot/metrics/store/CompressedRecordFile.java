/*
 * Copyright 2026 Arif Banai (arif-banai).
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
package com.jagrosh.jmusicbot.metrics.store;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * A thread-safe file format for storing compressed, length-prefixed binary records.
 * 
 * <p>File format:
 * <ul>
 *   <li>4 bytes: Magic header to identify file format and version</li>
 *   <li>For each record:
 *     <ul>
 *       <li>4 bytes: Length of compressed data (big-endian int)</li>
 *       <li>N bytes: GZIP-compressed data</li>
 *     </ul>
 *   </li>
 * </ul>
 * 
 * <p>This class is designed for append-only workloads where each record is
 * independently compressed, allowing efficient appends without rewriting
 * the entire file.
 *
 * @author Arif Banai (arif-banai)
 */
public class CompressedRecordFile {
    private static final Logger LOG = LoggerFactory.getLogger(CompressedRecordFile.class);
    
    // Maximum record size to prevent memory issues (1MB)
    private static final int MAX_RECORD_SIZE = 1024 * 1024;

    private final Path filePath;
    private final byte[] magic;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * Creates a new CompressedRecordFile.
     *
     * @param filePath Path to the file
     * @param magic    4-byte magic header to identify the file format
     */
    public CompressedRecordFile(Path filePath, byte[] magic) {
        if (magic.length != 4) {
            throw new IllegalArgumentException("Magic header must be exactly 4 bytes");
        }
        this.filePath = filePath;
        this.magic = magic.clone();
    }

    /**
     * Appends a record to the file.
     * The data is GZIP-compressed before writing.
     *
     * @param data The data to append (will be compressed)
     * @throws IOException If an I/O error occurs
     */
    public void appendRecord(byte[] data) throws IOException {
        lock.writeLock().lock();
        try {
            byte[] compressed = compress(data);
            
            try (OutputStream out = Files.newOutputStream(filePath, 
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                
                // Write magic header if this is a new file
                if (Files.size(filePath) == 0) {
                    out.write(magic);
                }
                
                // Write length (4 bytes, big-endian) + compressed data
                ByteBuffer lengthBuffer = ByteBuffer.allocate(4);
                lengthBuffer.putInt(compressed.length);
                out.write(lengthBuffer.array());
                out.write(compressed);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Appends a string record to the file.
     * Convenience method that handles UTF-8 encoding.
     *
     * @param data The string data to append
     * @throws IOException If an I/O error occurs
     */
    public void appendRecord(String data) throws IOException {
        appendRecord(data.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Reads all records from the file.
     *
     * @return List of decompressed records as byte arrays
     * @throws IOException If an I/O error occurs
     */
    public List<byte[]> readAllRecords() throws IOException {
        lock.readLock().lock();
        try {
            if (!Files.exists(filePath) || Files.size(filePath) == 0) {
                return new ArrayList<>();
            }
            
            List<byte[]> records = new ArrayList<>();
            try (InputStream in = Files.newInputStream(filePath)) {
                // Verify magic header
                byte[] fileMagic = new byte[4];
                if (in.read(fileMagic) != 4 || !java.util.Arrays.equals(fileMagic, magic)) {
                    LOG.warn("Invalid file format or magic header mismatch");
                    return new ArrayList<>();
                }
                
                // Read length-prefixed records
                byte[] lengthBytes = new byte[4];
                while (in.read(lengthBytes) == 4) {
                    int length = ByteBuffer.wrap(lengthBytes).getInt();
                    if (length <= 0 || length > MAX_RECORD_SIZE) {
                        LOG.warn("Invalid record length: {}", length);
                        break;
                    }
                    
                    byte[] compressed = new byte[length];
                    int bytesRead = in.read(compressed);
                    if (bytesRead != length) {
                        LOG.warn("Incomplete record, expected {} bytes but got {}", length, bytesRead);
                        break;
                    }
                    
                    records.add(decompress(compressed));
                }
            }
            return records;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Reads all records from the file as strings.
     * Convenience method that handles UTF-8 decoding.
     *
     * @return List of decompressed records as strings
     * @throws IOException If an I/O error occurs
     */
    public List<String> readAllRecordsAsStrings() throws IOException {
        List<byte[]> records = readAllRecords();
        List<String> strings = new ArrayList<>(records.size());
        for (byte[] record : records) {
            strings.add(new String(record, StandardCharsets.UTF_8));
        }
        return strings;
    }

    /**
     * Writes multiple records to the file, replacing any existing content.
     *
     * @param records The records to write
     * @throws IOException If an I/O error occurs
     */
    public void writeAllRecords(List<byte[]> records) throws IOException {
        lock.writeLock().lock();
        try {
            Files.deleteIfExists(filePath);
            for (byte[] record : records) {
                appendRecordInternal(record);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Deletes the file if it exists.
     *
     * @throws IOException If an I/O error occurs
     */
    public void delete() throws IOException {
        lock.writeLock().lock();
        try {
            Files.deleteIfExists(filePath);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Checks if the file exists and has content beyond the magic header.
     *
     * @return true if the file has records
     */
    public boolean hasRecords() {
        lock.readLock().lock();
        try {
            if (!Files.exists(filePath)) {
                return false;
            }
            return Files.size(filePath) > magic.length;
        } catch (IOException e) {
            return false;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Gets the file path.
     *
     * @return The path to the file
     */
    public Path getFilePath() {
        return filePath;
    }

    /**
     * Internal append without acquiring lock (for use within locked sections).
     */
    private void appendRecordInternal(byte[] data) throws IOException {
        byte[] compressed = compress(data);
        
        try (OutputStream out = Files.newOutputStream(filePath, 
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            
            if (Files.size(filePath) == 0) {
                out.write(magic);
            }
            
            ByteBuffer lengthBuffer = ByteBuffer.allocate(4);
            lengthBuffer.putInt(compressed.length);
            out.write(lengthBuffer.array());
            out.write(compressed);
        }
    }

    /**
     * Compresses data using GZIP.
     */
    private static byte[] compress(byte[] data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzos = new GZIPOutputStream(baos)) {
            gzos.write(data);
        }
        return baos.toByteArray();
    }

    /**
     * Decompresses GZIP data.
     */
    private static byte[] decompress(byte[] compressed) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(compressed);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPInputStream gzis = new GZIPInputStream(bais)) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = gzis.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
        }
        return baos.toByteArray();
    }
}
