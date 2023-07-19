package xyz.xenondevs.nova.addon.vanillahammers.item.options

import xyz.xenondevs.nova.data.config.ConfigAccess
import xyz.xenondevs.nova.item.NovaItem

class HammerOptions(material: NovaItem) : ConfigAccess(material) {
    
    val range by getEntry<Int>("range")
    val depth by getEntry<Int>("depth")
    val hardnessTolerance by getEntry<Double>("hardness_tolerance")
    
}