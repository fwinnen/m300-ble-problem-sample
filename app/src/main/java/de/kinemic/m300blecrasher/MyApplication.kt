package de.kinemic.m300blecrasher

import android.app.Application
import de.kinemic.gesture.DebugBleEngine
import de.kinemic.gesture.Engine
import de.kinemic.gesture.EngineOptions
import de.kinemic.gesture.common.EngineApplication
import de.kinemic.gesture.common.EngineProvider

class MyApplication: Application(), EngineProvider {
    override var lastConnectedBand: String? = null

    override val engine: Engine by lazy {
        DebugBleEngine(applicationContext, EngineOptions())
    }
}