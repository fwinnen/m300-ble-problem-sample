package de.kinemic.gesture;

import android.content.Context;
import androidx.annotation.NonNull;

/** only switches BleManager with custom subclass */
public class DebugBleEngine extends Engine {
    public DebugBleEngine(@NonNull Context context, @NonNull EngineOptions options) {
        super(createEngine(context, options));
    }

    private static InternalEngine createEngine(@NonNull Context context, @NonNull EngineOptions options) {
        AndroidResourceManager androidResourceManager = new AndroidResourceManager(context);
        InternalBleManager bleManager = new DebugAndroidBleManager(context); // custom subclass here
        InternalPlatformThreads platformThreads = new JavaPlatformThreads();
        return InternalEngine.create(androidResourceManager, bleManager, platformThreads, options.underlying);
    }


}
