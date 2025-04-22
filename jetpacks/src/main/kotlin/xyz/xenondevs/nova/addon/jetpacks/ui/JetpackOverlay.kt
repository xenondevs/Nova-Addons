package xyz.xenondevs.nova.addon.jetpacks.ui

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.ShadowColor
import xyz.xenondevs.nova.ui.overlay.actionbar.ActionbarOverlay
import xyz.xenondevs.nova.util.component.adventure.font
import xyz.xenondevs.nova.util.component.adventure.move

class JetpackOverlay : ActionbarOverlay {
    
    override var component: Component = getCurrentComponent()
        private set
    
    var percentage: Double = 0.0
        set(value) {
            require(value in 0.0..1.0)
            if (field == value) return
            field = value
            component = getCurrentComponent()
        }
    
    private fun getCurrentComponent(): Component {
        val stage = (percentage * 38).toInt()
        
        return Component.text()
            .move(95)
            .append(
                Component.text(('\uF000'.code + stage).toChar().toString())
                    .font("jetpacks:energy_bar")
            )
            .shadowColor(ShadowColor.none())
            .build()
    }
    
}