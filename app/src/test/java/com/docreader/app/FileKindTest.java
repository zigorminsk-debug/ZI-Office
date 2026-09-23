package com.docreader.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Тесты чистой логики FileKind (без Android-зависимостей). */
public class FileKindTest {

    // ---------- ext() ----------

    @Test public void ext_basic() {
        assertEquals("xlsx", FileKind.ext("report.xlsx"));
        assertEquals("pdf", FileKind.ext("file.PDF"));
        assertEquals("docx", FileKind.ext("path/to/file.docx"));
        assertEquals("xls", FileKind.ext("a.xls"));
        assertEquals("txt", FileKind.ext("a.b.c.txt"));
    }

    @Test public void ext_no_extension() {
        assertEquals("", FileKind.ext("noext"));
        assertEquals("", FileKind.ext(""));
        assertEquals("", FileKind.ext((String) null));
    }

    @Test public void ext_query_stripped() {
        assertEquals("pdf", FileKind.ext("file.pdf?token=abc"));
    }

    // ---------- office() ----------

    @Test public void office_supported() {
        for (String e : new String[]{"pdf", "doc", "docx", "rtf", "xls", "xlsx", "xlsm",
                "xlsb", "xltx", "xltm", "csv", "ods", "ppt", "pptx", "txt"}) {
            assertTrue(e, FileKind.office(e));
        }
    }

    @Test public void office_unsupported() {
        assertFalse(FileKind.office("jpg"));
        assertFalse(FileKind.office("zip"));
        assertFalse(FileKind.office(""));
        assertFalse(FileKind.office(null));
    }

    // ---------- fromNameAndMime() ----------

    @Test public void byName() {
        assertEquals("pdf", FileKind.fromNameAndMime("Report.pdf", null));
        assertEquals("docx", FileKind.fromNameAndMime("doc.docx", null));
        assertEquals("txt", FileKind.fromNameAndMime("a.txt", null));
        assertEquals("csv", FileKind.fromNameAndMime("g.csv", null));
    }

    @Test public void byMime() {
        assertEquals("pdf", FileKind.fromNameAndMime("file", "application/pdf"));
        assertEquals("docx", FileKind.fromNameAndMime("file",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
        assertEquals("doc", FileKind.fromNameAndMime("file", "application/msword"));
        assertEquals("xlsx", FileKind.fromNameAndMime("file",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        assertEquals("xls", FileKind.fromNameAndMime("file", "application/vnd.ms-excel"));
        assertEquals("pptx", FileKind.fromNameAndMime("file",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation"));
        assertEquals("ppt", FileKind.fromNameAndMime("file", "application/vnd.ms-powerpoint"));
        assertEquals("ods", FileKind.fromNameAndMime("file", "application/vnd.oasis.opendocument.spreadsheet"));
        assertEquals("txt", FileKind.fromNameAndMime("f", "text/plain"));
        assertEquals("csv", FileKind.fromNameAndMime("h", "text/csv"));
    }

    @Test public void byNameHints() {
        // «xlsx внутри» — имя с двойным расширением
        assertEquals("xlsx", FileKind.fromNameAndMime("data.xlsx.bin", null));
        assertEquals("xls", FileKind.fromNameAndMime("data.xls.dat", null));
        assertEquals("ods", FileKind.fromNameAndMime("book.ods.tar", null));
    }

    @Test public void unknown() {
        assertEquals(FileKind.UNKNOWN, FileKind.fromNameAndMime("data.bin", "application/octet-stream"));
        assertEquals(FileKind.UNKNOWN, FileKind.fromNameAndMime("a.zip", "application/zip"));
        assertEquals(FileKind.UNKNOWN, FileKind.fromNameAndMime("nothing", null));
    }

    // ---------- viewerKind() ----------

    @Test public void viewerKind_mapping() {
        assertEquals("pdf", FileKind.viewerKind("pdf"));
        assertEquals("word", FileKind.viewerKind("docx"));
        assertEquals("word", FileKind.viewerKind("doc"));
        assertEquals("excel", FileKind.viewerKind("xlsx"));
        assertEquals("excel", FileKind.viewerKind("csv"));
        assertEquals("ppt", FileKind.viewerKind("pptx"));
        assertEquals("text", FileKind.viewerKind("txt"));
        // неопознанное → по умолчанию pdf
        assertEquals("pdf", FileKind.viewerKind(""));
        assertEquals("pdf", FileKind.viewerKind(null));
        assertEquals("pdf", FileKind.viewerKind("zzz"));
    }

    // ---------- mimeForExt() ----------

    @Test public void mimeForExt_values() {
        assertEquals("application/pdf", FileKind.mimeForExt("pdf"));
        assertEquals("application/msword", FileKind.mimeForExt("doc"));
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                FileKind.mimeForExt("docx"));
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                FileKind.mimeForExt("xlsx"));
        assertEquals("application/vnd.oasis.opendocument.spreadsheet", FileKind.mimeForExt("ods"));
        assertEquals("text/csv", FileKind.mimeForExt("csv"));
        assertEquals("application/octet-stream", FileKind.mimeForExt("unknown"));
        assertEquals("application/octet-stream", FileKind.mimeForExt(null));
    }

    // ---------- displayExt() / color() ----------

    @Test public void displayExt() {
        assertEquals("PDF", FileKind.displayExt("a.pdf", "pdf"));
        assertEquals("XLSX", FileKind.displayExt("noext", "xlsx"));
        assertEquals("FILE", FileKind.displayExt("a", null));
        assertEquals("FILE", FileKind.displayExt("a", FileKind.UNKNOWN));
    }

    @Test public void color_byKind() {
        assertEquals(1, FileKind.color("pdf", 1, 2, 3, 4, 5));
        assertEquals(2, FileKind.color("docx", 1, 2, 3, 4, 5));
        assertEquals(3, FileKind.color("xls", 1, 2, 3, 4, 5));
        assertEquals(4, FileKind.color("pptx", 1, 2, 3, 4, 5));
        assertEquals(5, FileKind.color(null, 1, 2, 3, 4, 5));
        assertEquals(5, FileKind.color("zzz", 1, 2, 3, 4, 5));
    }

    // ---------- sniff() ----------

    private File tempFile(byte[] data) throws IOException {
        File f = File.createTempFile("zi-test-", ".bin");
        f.deleteOnExit();
        try (FileOutputStream fo = new FileOutputStream(f)) { fo.write(data); }
        return f;
    }

    @Test public void sniff_pdf() throws IOException {
        assertEquals("pdf", FileKind.sniff(tempFile("%PDF-1.7 fake".getBytes())));
    }

    @Test public void sniff_ole() throws IOException {
        byte[] ole = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, 1, 2, 3, 4};
        assertEquals("xls", FileKind.sniff(tempFile(ole)));
    }

    @Test public void sniff_zip_xlsx() throws IOException {
        File f = File.createTempFile("zi-test-", ".zip");
        f.deleteOnExit();
        try (ZipOutputStream zo = new ZipOutputStream(new FileOutputStream(f))) {
            zo.putNextEntry(new ZipEntry("xl/workbook.xml"));
            zo.write("<x/>".getBytes());
            zo.closeEntry();
        }
        assertEquals("xlsx", FileKind.sniff(f));
    }

    @Test public void sniff_zip_docx() throws IOException {
        File f = File.createTempFile("zi-test-", ".zip");
        f.deleteOnExit();
        try (ZipOutputStream zo = new ZipOutputStream(new FileOutputStream(f))) {
            zo.putNextEntry(new ZipEntry("word/document.xml"));
            zo.write("<w/>".getBytes());
            zo.closeEntry();
        }
        assertEquals("docx", FileKind.sniff(f));
    }

    @Test public void sniff_broken_zip() throws IOException {
        byte[] bad = {'P', 'K', 0x03, 0x04, 'g', 'a', 'r', 'b', 'a', 'g', 'e'};
        assertEquals(FileKind.UNKNOWN, FileKind.sniff(tempFile(bad)));
    }

    @Test public void sniff_tooShort() throws IOException {
        assertEquals(FileKind.UNKNOWN, FileKind.sniff(tempFile(new byte[]{1, 2, 3})));
        assertEquals(FileKind.UNKNOWN, FileKind.sniff(null));
    }
}
