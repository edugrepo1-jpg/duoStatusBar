package io.github.kvmy666.duostatusbar.fx

/** Process-local bounded progress evidence. No notification text, files or dismissal inference. */
internal class NotificationProgressTracker(private val capacity:Int=128) {
    private data class Progress(val value:Int,val maximum:Int)
    private val states=LinkedHashMap<String,Progress>()
    fun observe(key:String,value:Int,maximum:Int,determinate:Boolean,seed:Boolean=false):Boolean {
        if(key.length>1024)return false
        if(!determinate||maximum<=0||value<0){states.remove(key);return false}
        val previous=states.remove(key)
        states[key]=Progress(value,maximum)
        while(states.size>capacity.coerceAtLeast(1))states.remove(states.keys.first())
        return !seed&&previous!=null&&previous.value<previous.maximum&&value>=maximum
    }
    fun remove(key:String){states.remove(key)}
    fun clear(){states.clear()}
    internal val size get()=states.size
}
