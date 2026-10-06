package io.github.kvmy666.duostatusbar.ui

import android.content.Context
import io.github.kvmy666.duostatusbar.fx.ExperienceOptions
import io.github.kvmy666.duostatusbar.settings.*

/** A shared duration affects both orientations without replacing either orientation's other options. */
internal object TimingPreferences {
    fun publish(context:Context,before:DuoSettings,next:DuoSettings,orientation:DuoOrientation) {
        val old=ExperienceOptions.decode(before.experienceJson)
        val new=ExperienceOptions.decode(next.experienceJson)
        if(!new.universalTiming || (old.universalTiming==new.universalTiming&&old.dwellMs==new.dwellMs))return
        val other=if(orientation==DuoOrientation.PORTRAIT)DuoOrientation.LANDSCAPE else DuoOrientation.PORTRAIT
        val saved=DuoPrefs.read(context,other)
        val options=ExperienceOptions.decode(saved.experienceJson).copy(universalTiming=true,dwellMs=new.dwellMs)
        DuoPrefs.write(context,saved.copy(experienceJson=options.encode()),other)
    }
}
