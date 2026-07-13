package app.morphe.extension.shared;

import android.content.Context;
import com.tivimate.traktpatch.extension.TraktPatchExtension;

/** Morphe shared-extension entry point used by the proven helper patch. */
@SuppressWarnings("unused")
public final class Utils {
    private Utils() {}

    public static void initialize(Context context) {
        TraktPatchExtension.initialize(context);
    }
}
