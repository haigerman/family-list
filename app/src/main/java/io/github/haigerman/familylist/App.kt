package io.github.haigerman.familylist

import android.app.Application
import io.github.haigerman.familylist.data.Store
import io.github.haigerman.familylist.notify.Reminders
import io.github.haigerman.familylist.notify.SyncWorker

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        Reminders.refresh(this, Store.data.value)
        SyncWorker.schedule(this)
    }
}
