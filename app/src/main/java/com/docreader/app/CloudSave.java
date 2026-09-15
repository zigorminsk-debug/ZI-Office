package com.docreader.app;
import android.content.Context; import android.net.Uri; import android.provider.DocumentsContract; import java.io.OutputStream;
final class CloudSave {
    static boolean has(Context ctx, String cloud) { return CloudTrees.get(ctx, cloud) != null; }
    static Uri create(Context ctx, String cloud, String mime, String name) {
        Uri tree = CloudTrees.get(ctx, cloud); if (tree == null) return null;
        try {
            String id = DocumentsContract.getTreeDocumentId(tree);
            Uri parent = DocumentsContract.buildDocumentUriUsingTree(tree, id);
            return DocumentsContract.createDocument(ctx.getContentResolver(), parent, mime == null || mime.isEmpty() ? "application/octet-stream" : mime, name == null || name.isEmpty() ? "document" : name);
        } catch (Exception e) { return null; }
    }
    static boolean write(Context ctx, Uri uri, byte[] data) {
        if (uri == null || data == null) return false;
        try (OutputStream out = ctx.getContentResolver().openOutputStream(uri, "wt")) { if (out == null) return false; out.write(data); return true; }
        catch (Exception e) {
            try (OutputStream out = ctx.getContentResolver().openOutputStream(uri)) { if (out == null) return false; out.write(data); return true; } catch (Exception e2) { return false; }
        }
    }
}
