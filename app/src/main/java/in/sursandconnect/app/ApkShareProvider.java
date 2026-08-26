package in.sursandconnect.app;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

public class ApkShareProvider extends ContentProvider {

    @Override
    public boolean onCreate() {
        return true;
    }

    private File getApkFile() {
        return new File(new File(getContext().getCacheDir(), "shared_apk"), "Sursand-Connect.apk");
    }

    @Override
    public String getType(Uri uri) {
        return "application/vnd.android.package-archive";
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File apk = getApkFile();
        if (!apk.exists()) throw new FileNotFoundException("APK not prepared");
        return ParcelFileDescriptor.open(apk, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        File apk = getApkFile();
        MatrixCursor cursor = new MatrixCursor(
            new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}
        );
        cursor.addRow(new Object[]{"Sursand-Connect.apk", apk.exists() ? apk.length() : 0});
        return cursor;
    }

    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
}
