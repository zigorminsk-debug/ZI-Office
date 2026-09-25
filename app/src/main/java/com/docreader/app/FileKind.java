package com.docreader.app;
import android.content.Context; import android.database.Cursor; import android.net.Uri; import android.provider.OpenableColumns; import android.webkit.MimeTypeMap;
final class FileKind {
    static final String UNKNOWN = "unknown";
    static String name(Context ctx, Uri uri) {
        if (uri == null) return "document"; String n = null;
        try (Cursor c = ctx.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) { if (c != null && c.moveToFirst()) n = c.getString(0); } catch (Exception ignored) {}
        if (n == null || n.isEmpty()) n = uri.getLastPathSegment(); return n == null ? "document" : n;
    }
    static String mime(Context ctx, Uri uri) {
        String m = null; try { m = ctx.getContentResolver().getType(uri); } catch (Exception ignored) {}
        if (m != null && !m.isEmpty()) return m;
        String n = name(ctx, uri); int d = n.lastIndexOf('.');
        if (d >= 0) { String e = MimeTypeMap.getSingleton().getMimeTypeFromExtension(n.substring(d + 1).toLowerCase()); if (e != null) return e; }
        return "application/octet-stream";
    }
    static String ext(String name) {
        if (name == null) return "";
        String n = name; int q = n.indexOf('?'); if (q >= 0) n = n.substring(0, q);
        n = n.replace('\\', '/'); int slash = n.lastIndexOf('/'); if (slash >= 0) n = n.substring(slash + 1);
        n = n.toLowerCase();
        if (n.endsWith(".xlsx") || n.endsWith(".xlsm") || n.endsWith(".xlsb") || n.endsWith(".xltx") || n.endsWith(".xltm") || n.endsWith(".xls")) {
            int d = n.lastIndexOf('.'); return n.substring(d + 1);
        }
        int d = n.lastIndexOf('.'); if (d < 0) return "";
        String e = n.substring(d + 1); int cut = e.indexOf('/'); if (cut >= 0) e = e.substring(0, cut); return e;
    }
    static boolean office(String e) {
        return "pdf".equals(e) || "doc".equals(e) || "docx".equals(e) || "rtf".equals(e)
                || "xls".equals(e) || "xlsx".equals(e) || "xlsm".equals(e) || "xlsb".equals(e) || "xltx".equals(e) || "xltm".equals(e)
                || "csv".equals(e) || "ods".equals(e) || "ppt".equals(e) || "pptx".equals(e) || "txt".equals(e);
    }
    static String fromNameAndMime(String name, String mime) {
        String e = ext(name); if (office(e)) return e;
        if (name != null) {
            String low = name.toLowerCase();
            if (low.contains(".xlsx") || low.contains(".xlsm") || low.contains(".xltx")) return "xlsx";
            if (low.contains(".xlsb")) return "xlsb";
            if (low.contains(".xls")) return "xls";
            if (low.contains(".ods")) return "ods";
        }
        if (mime != null) {
            String m = mime.toLowerCase();
            if (m.contains("pdf")) return "pdf"; if (m.contains("rtf")) return "rtf";
            if (m.contains("word") || m.contains("msword") || m.contains("wordprocessing")) return m.contains("openxml") || m.contains("docx") ? "docx" : "doc";
            if (m.contains("csv") || m.contains("comma-separated")) return "csv";
            if (m.contains("spreadsheetml") || m.contains("xlsx") || m.contains("xlsm") || m.contains("xltx")) return "xlsx";
            if (m.contains("opendocument.spreadsheet") || m.contains("vnd.oasis.opendocument.spreadsheet")) return "ods";
            if (m.contains("excel") || m.contains("spreadsheet") || m.contains("vnd.ms-excel") || m.contains("numbers")) return m.contains("openxml") ? "xlsx" : "xls";
            if (m.contains("opendocument.presentation")) return UNKNOWN;
            if (m.contains("powerpoint") || m.contains("presentation")) return m.contains("openxml") ? "pptx" : "ppt";
            if (m.contains("text/plain") || m.contains("text/txt")) return "txt";
            if (m.contains("application/zip") || m.contains("octet-stream") || m.contains("application/x-zip")) return UNKNOWN;
        }
        return UNKNOWN;
    }
    static String sniff(java.io.File file) {
        if (file == null || !file.exists() || file.length() < 4) return UNKNOWN;
        byte[] b = new byte[8];
        try (java.io.FileInputStream in = new java.io.FileInputStream(file)) {
            int n = in.read(b); if (n < 4) return UNKNOWN;
        } catch (Exception e) { return UNKNOWN; }
        if (b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F') return "pdf";
        if ((b[0] & 0xFF) == 0xD0 && (b[1] & 0xFF) == 0xCF && (b[2] & 0xFF) == 0x11 && (b[3] & 0xFF) == 0xE0) return "xls";
        if (b[0] == 'P' && b[1] == 'K') {
            String names = zipEntryHint(file);
            if (names.contains("xl/") || names.contains("workbook.xml") || names.contains("spreadsheetml")) return "xlsx";
            if (names.contains("word/") || names.contains("wordprocessingml")) return "docx";
            if (names.contains("ppt/") || names.contains("presentationml")) return "pptx";
            if (names.contains("encryptedpackage")) return "xlsx";
            if (names.contains("content.xml") && names.contains("mimetype")) return "ods";
            return UNKNOWN;
        }
        try (java.io.FileInputStream in = new java.io.FileInputStream(file)) {
            byte[] head = new byte[(int) Math.min(256, file.length())]; int n = in.read(head);
            String s = new String(head, 0, Math.max(0, n), java.nio.charset.StandardCharsets.ISO_8859_1).toLowerCase();
            if (s.contains("spreadsheetml") || s.contains("workbook") || s.contains("ss:worksheet")) return "xls";
        } catch (Exception ignored) {}
        return UNKNOWN;
    }
    private static String zipEntryHint(java.io.File file) {
        StringBuilder sb = new StringBuilder();
        try (java.util.zip.ZipFile z = new java.util.zip.ZipFile(file)) {
            java.util.Enumeration<? extends java.util.zip.ZipEntry> en = z.entries(); int i = 0;
            while (en.hasMoreElements() && i++ < 80) {
                java.util.zip.ZipEntry e = en.nextElement();
                if (e.getName() != null) sb.append(e.getName().toLowerCase()).append('\n');
            }
        } catch (Exception ignored) {
            try (java.util.zip.ZipInputStream zs = new java.util.zip.ZipInputStream(new java.io.FileInputStream(file))) {
                java.util.zip.ZipEntry e; int i = 0;
                while ((e = zs.getNextEntry()) != null && i++ < 80) {
                    if (e.getName() != null) sb.append(e.getName().toLowerCase()).append('\n');
                }
            } catch (Exception ignored2) {}
        }
        return sb.toString();
    }
    /** Имя файла без каталогов и опасных символов — годится для своей папки. */
    static String safeFileName(String name) {
        if (name == null) return "file";
        String n = name.replace('\\', '/');
        int slash = n.lastIndexOf('/'); if (slash >= 0) n = n.substring(slash + 1);
        n = n.replaceAll("[^\\p{L}\\p{N}._ ()\\[\\]-]", "_").trim();
        if (n.isEmpty() || n.startsWith(".")) n = "file-" + n;
        return n.length() > 120 ? n.substring(n.length() - 120) : n;
    }

    static String displayExt(String name, String kind) {
        String e = ext(name); if (!e.isEmpty()) return e.toUpperCase();
        if (kind != null && !UNKNOWN.equals(kind) && !kind.isEmpty()) return kind.toUpperCase(); return "FILE";
    }
    static int color(String kind, int pdf, int word, int excel, int ppt, int teal) {
        if (kind == null) return teal;
        switch (kind) {
            case "pdf": return pdf;
            case "doc": case "docx": case "rtf": case "txt": case "word": case "text": return word;
            case "xls": case "xlsx": case "xlsm": case "xlsb": case "xltx": case "xltm": case "csv": case "ods": case "excel": return excel;
            case "ppt": case "pptx": return ppt; default: return teal;
        }
    }
    static String viewerKind(String extOrKind) {
        if (extOrKind == null) return "pdf"; String e = extOrKind.toLowerCase();
        if ("pdf".equals(e)) return "pdf";
        if ("doc".equals(e) || "docx".equals(e) || "rtf".equals(e) || "word".equals(e)) return "word";
        if ("xls".equals(e) || "xlsx".equals(e) || "xlsm".equals(e) || "xlsb".equals(e) || "xltx".equals(e) || "xltm".equals(e) || "csv".equals(e) || "ods".equals(e) || "excel".equals(e)) return "excel";
        if ("ppt".equals(e) || "pptx".equals(e)) return "ppt";
        if ("txt".equals(e) || "text".equals(e)) return "text";
        return "pdf";
    }
    static String mimeForExt(String e) {
        if (e == null) return "application/octet-stream";
        switch (e) {
            case "pdf": return "application/pdf"; case "doc": return "application/msword";
            case "docx": return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "rtf": return "application/rtf"; case "xls": return "application/vnd.ms-excel";
            case "xlsx": case "xlsm": case "xltx": case "xltm": return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "xlsb": return "application/vnd.ms-excel.sheet.binary.macroEnabled.12";
            case "ods": return "application/vnd.oasis.opendocument.spreadsheet";
            case "csv": return "text/csv"; case "ppt": return "application/vnd.ms-powerpoint";
            case "pptx": return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "txt": return "text/plain"; default: return "application/octet-stream";
        }
    }
}
