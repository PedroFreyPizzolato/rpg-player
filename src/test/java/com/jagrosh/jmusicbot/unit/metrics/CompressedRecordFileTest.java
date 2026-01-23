/*
 * Copyright 2026 John Grosh (jagrosh).
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
package com.jagrosh.jmusicbot.unit.metrics;

import com.jagrosh.jmusicbot.metrics.store.CompressedRecordFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CompressedRecordFile Unit Tests")
class CompressedRecordFileTest {

    @TempDir
    Path tempDir;
    
    private static final byte[] TEST_MAGIC = {'T', 'E', 'S', 'T'};
    private CompressedRecordFile recordFile;

    @BeforeEach
    void setUp() {
        recordFile = new CompressedRecordFile(tempDir.resolve("test.bin"), TEST_MAGIC);
    }

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Constructor throws if magic is not 4 bytes")
        void constructorThrowsIfMagicWrongSize() {
            assertThrows(IllegalArgumentException.class, () ->
                    new CompressedRecordFile(tempDir.resolve("test.bin"), new byte[]{1, 2, 3}));
            assertThrows(IllegalArgumentException.class, () ->
                    new CompressedRecordFile(tempDir.resolve("test.bin"), new byte[]{1, 2, 3, 4, 5}));
        }

        @Test
        @DisplayName("Constructor accepts exactly 4 bytes")
        void constructorAccepts4Bytes() {
            assertDoesNotThrow(() ->
                    new CompressedRecordFile(tempDir.resolve("test.bin"), new byte[]{1, 2, 3, 4}));
        }
    }

    @Nested
    @DisplayName("appendRecord() Tests")
    class AppendRecordTests {

        @Test
        @DisplayName("appendRecord() creates file if it doesn't exist")
        void appendRecordCreatesFile() throws IOException {
            recordFile.appendRecord("test data");
            
            assertTrue(Files.exists(recordFile.getFilePath()));
        }

        @Test
        @DisplayName("appendRecord() writes magic header for new file")
        void appendRecordWritesMagicHeader() throws IOException {
            recordFile.appendRecord("test data");
            
            byte[] bytes = Files.readAllBytes(recordFile.getFilePath());
            assertEquals('T', bytes[0]);
            assertEquals('E', bytes[1]);
            assertEquals('S', bytes[2]);
            assertEquals('T', bytes[3]);
        }

        @Test
        @DisplayName("appendRecord() can append multiple records")
        void appendRecordCanAppendMultiple() throws IOException {
            recordFile.appendRecord("record 1");
            recordFile.appendRecord("record 2");
            recordFile.appendRecord("record 3");
            
            List<String> records = recordFile.readAllRecordsAsStrings();
            assertEquals(3, records.size());
            assertEquals("record 1", records.get(0));
            assertEquals("record 2", records.get(1));
            assertEquals("record 3", records.get(2));
        }

        @Test
        @DisplayName("appendRecord() compresses data")
        void appendRecordCompressesData() throws IOException {
            // Create a highly compressible string
            String data = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
            recordFile.appendRecord(data);
            
            long fileSize = Files.size(recordFile.getFilePath());
            // 4 (magic) + 4 (length) + compressed data should be much less than 64 + overhead
            assertTrue(fileSize < 50, "File should be compressed, actual size: " + fileSize);
        }

        @Test
        @DisplayName("appendRecord() accepts byte array")
        void appendRecordAcceptsByteArray() throws IOException {
            byte[] data = {0x01, 0x02, 0x03, 0x04, 0x05};
            recordFile.appendRecord(data);
            
            List<byte[]> records = recordFile.readAllRecords();
            assertEquals(1, records.size());
            assertArrayEquals(data, records.get(0));
        }
    }

    @Nested
    @DisplayName("readAllRecords() Tests")
    class ReadAllRecordsTests {

        @Test
        @DisplayName("readAllRecords() returns empty list for non-existent file")
        void readAllRecordsReturnsEmptyForNonExistent() throws IOException {
            List<byte[]> records = recordFile.readAllRecords();
            assertTrue(records.isEmpty());
        }

        @Test
        @DisplayName("readAllRecords() returns empty list for file with wrong magic")
        void readAllRecordsReturnsEmptyForWrongMagic() throws IOException {
            // Write a file with different magic
            CompressedRecordFile otherFile = new CompressedRecordFile(
                    recordFile.getFilePath(), new byte[]{'O', 'T', 'H', 'R'});
            otherFile.appendRecord("test");
            
            // Try to read with our magic
            List<byte[]> records = recordFile.readAllRecords();
            assertTrue(records.isEmpty());
        }

        @Test
        @DisplayName("readAllRecords() preserves record order")
        void readAllRecordsPreservesOrder() throws IOException {
            for (int i = 0; i < 100; i++) {
                recordFile.appendRecord("record-" + i);
            }
            
            List<String> records = recordFile.readAllRecordsAsStrings();
            assertEquals(100, records.size());
            for (int i = 0; i < 100; i++) {
                assertEquals("record-" + i, records.get(i));
            }
        }
    }

    @Nested
    @DisplayName("writeAllRecords() Tests")
    class WriteAllRecordsTests {

        @Test
        @DisplayName("writeAllRecords() replaces existing content")
        void writeAllRecordsReplacesContent() throws IOException {
            recordFile.appendRecord("old record 1");
            recordFile.appendRecord("old record 2");
            
            List<byte[]> newRecords = List.of(
                    "new record".getBytes()
            );
            recordFile.writeAllRecords(newRecords);
            
            List<String> records = recordFile.readAllRecordsAsStrings();
            assertEquals(1, records.size());
            assertEquals("new record", records.get(0));
        }

        @Test
        @DisplayName("writeAllRecords() can write empty list")
        void writeAllRecordsCanWriteEmptyList() throws IOException {
            recordFile.appendRecord("existing");
            
            recordFile.writeAllRecords(List.of());
            
            assertFalse(recordFile.hasRecords());
        }
    }

    @Nested
    @DisplayName("delete() Tests")
    class DeleteTests {

        @Test
        @DisplayName("delete() removes the file")
        void deleteRemovesFile() throws IOException {
            recordFile.appendRecord("test");
            assertTrue(Files.exists(recordFile.getFilePath()));
            
            recordFile.delete();
            
            assertFalse(Files.exists(recordFile.getFilePath()));
        }

        @Test
        @DisplayName("delete() succeeds if file doesn't exist")
        void deleteSucceedsIfNoFile() throws IOException {
            assertDoesNotThrow(() -> recordFile.delete());
        }
    }

    @Nested
    @DisplayName("hasRecords() Tests")
    class HasRecordsTests {

        @Test
        @DisplayName("hasRecords() returns false for non-existent file")
        void hasRecordsReturnsFalseForNonExistent() {
            assertFalse(recordFile.hasRecords());
        }

        @Test
        @DisplayName("hasRecords() returns true when records exist")
        void hasRecordsReturnsTrueWithRecords() throws IOException {
            recordFile.appendRecord("test");
            assertTrue(recordFile.hasRecords());
        }

        @Test
        @DisplayName("hasRecords() returns false after delete")
        void hasRecordsReturnsFalseAfterDelete() throws IOException {
            recordFile.appendRecord("test");
            recordFile.delete();
            assertFalse(recordFile.hasRecords());
        }
    }

    @Nested
    @DisplayName("Thread Safety Tests")
    class ThreadSafetyTests {

        @Test
        @DisplayName("Concurrent appends don't corrupt data")
        void concurrentAppendsPreserveData() throws Exception {
            int numThreads = 10;
            int recordsPerThread = 100;
            Thread[] threads = new Thread[numThreads];
            
            for (int t = 0; t < numThreads; t++) {
                final int threadId = t;
                threads[t] = new Thread(() -> {
                    for (int i = 0; i < recordsPerThread; i++) {
                        try {
                            recordFile.appendRecord("thread-" + threadId + "-record-" + i);
                        } catch (IOException e) {
                            fail("IOException during append: " + e.getMessage());
                        }
                    }
                });
            }
            
            for (Thread thread : threads) {
                thread.start();
            }
            for (Thread thread : threads) {
                thread.join();
            }
            
            List<String> records = recordFile.readAllRecordsAsStrings();
            assertEquals(numThreads * recordsPerThread, records.size());
        }
    }

    @Nested
    @DisplayName("Compression Tests")
    class CompressionTests {

        @Test
        @DisplayName("Compresses repetitive data efficiently")
        void compressesRepetitiveDataEfficiently() throws IOException {
            // 1000 identical characters - should compress very well
            String repetitiveData = "a".repeat(1000);
            recordFile.appendRecord(repetitiveData);
            
            long fileSize = Files.size(recordFile.getFilePath());
            // Magic (4) + length (4) + compressed (should be < 50 bytes)
            assertTrue(fileSize < 60, "File should compress well, actual size: " + fileSize);
            
            // Verify data integrity
            List<String> records = recordFile.readAllRecordsAsStrings();
            assertEquals(1, records.size());
            assertEquals(repetitiveData, records.get(0));
        }

        @Test
        @DisplayName("Handles incompressible data")
        void handlesIncompressibleData() throws IOException {
            // Random-ish data that doesn't compress well
            byte[] randomData = new byte[256];
            for (int i = 0; i < 256; i++) {
                randomData[i] = (byte) i;
            }
            recordFile.appendRecord(randomData);
            
            List<byte[]> records = recordFile.readAllRecords();
            assertEquals(1, records.size());
            assertArrayEquals(randomData, records.get(0));
        }

        @Test
        @DisplayName("Multiple records maintain independent compression")
        void multipleRecordsMaintainIndependentCompression() throws IOException {
            recordFile.appendRecord("first record");
            long sizeAfterFirst = Files.size(recordFile.getFilePath());
            
            recordFile.appendRecord("second record");
            long sizeAfterSecond = Files.size(recordFile.getFilePath());
            
            assertTrue(sizeAfterSecond > sizeAfterFirst);
            
            List<String> records = recordFile.readAllRecordsAsStrings();
            assertEquals(2, records.size());
            assertEquals("first record", records.get(0));
            assertEquals("second record", records.get(1));
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Handles empty string record")
        void handlesEmptyStringRecord() throws IOException {
            recordFile.appendRecord("");
            
            List<String> records = recordFile.readAllRecordsAsStrings();
            assertEquals(1, records.size());
            assertEquals("", records.get(0));
        }

        @Test
        @DisplayName("Handles empty byte array record")
        void handlesEmptyByteArrayRecord() throws IOException {
            recordFile.appendRecord(new byte[0]);
            
            List<byte[]> records = recordFile.readAllRecords();
            assertEquals(1, records.size());
            assertEquals(0, records.get(0).length);
        }

        @Test
        @DisplayName("Handles unicode characters")
        void handlesUnicodeCharacters() throws IOException {
            String unicode = "日本語 中文 한국어 العربية émojis 🎵🎶🎸";
            recordFile.appendRecord(unicode);
            
            List<String> records = recordFile.readAllRecordsAsStrings();
            assertEquals(1, records.size());
            assertEquals(unicode, records.get(0));
        }

        @Test
        @DisplayName("Handles binary data with null bytes")
        void handlesBinaryDataWithNullBytes() throws IOException {
            byte[] binaryData = {0x00, 0x01, 0x00, 0x02, 0x00, 0x03};
            recordFile.appendRecord(binaryData);
            
            List<byte[]> records = recordFile.readAllRecords();
            assertEquals(1, records.size());
            assertArrayEquals(binaryData, records.get(0));
        }

        @Test
        @DisplayName("Handles large record")
        void handlesLargeRecord() throws IOException {
            // 100KB record
            byte[] largeData = new byte[100 * 1024];
            for (int i = 0; i < largeData.length; i++) {
                largeData[i] = (byte) (i % 256);
            }
            recordFile.appendRecord(largeData);
            
            List<byte[]> records = recordFile.readAllRecords();
            assertEquals(1, records.size());
            assertArrayEquals(largeData, records.get(0));
        }

        @Test
        @DisplayName("Handles newlines and special whitespace")
        void handlesNewlinesAndSpecialWhitespace() throws IOException {
            String data = "line1\nline2\rline3\r\nline4\ttabbed";
            recordFile.appendRecord(data);
            
            List<String> records = recordFile.readAllRecordsAsStrings();
            assertEquals(1, records.size());
            assertEquals(data, records.get(0));
        }

        @Test
        @DisplayName("Handles JSON-like content")
        void handlesJsonLikeContent() throws IOException {
            String json = "{\"key\":\"value\",\"nested\":{\"array\":[1,2,3]},\"unicode\":\"日本語\"}";
            recordFile.appendRecord(json);
            
            List<String> records = recordFile.readAllRecordsAsStrings();
            assertEquals(1, records.size());
            assertEquals(json, records.get(0));
        }
    }

    @Nested
    @DisplayName("Persistence Tests")
    class PersistenceTests {

        @Test
        @DisplayName("Data survives across file reopen")
        void dataSurvivesAcrossFileReopen() throws IOException {
            recordFile.appendRecord("persistent data");
            
            // Create new instance pointing to same file
            CompressedRecordFile newInstance = new CompressedRecordFile(
                    recordFile.getFilePath(), TEST_MAGIC);
            
            List<String> records = newInstance.readAllRecordsAsStrings();
            assertEquals(1, records.size());
            assertEquals("persistent data", records.get(0));
        }

        @Test
        @DisplayName("Multiple records survive across file reopen")
        void multipleRecordsSurviveAcrossFileReopen() throws IOException {
            for (int i = 0; i < 10; i++) {
                recordFile.appendRecord("record-" + i);
            }
            
            CompressedRecordFile newInstance = new CompressedRecordFile(
                    recordFile.getFilePath(), TEST_MAGIC);
            
            List<String> records = newInstance.readAllRecordsAsStrings();
            assertEquals(10, records.size());
            for (int i = 0; i < 10; i++) {
                assertEquals("record-" + i, records.get(i));
            }
        }
    }
}
