package com.kolesnikovprod.ksetaorch.communication.tools.builtin.flashlight

import com.kolesnikovprod.ksetaorch.communication.work.oneshot.KsenaxOneShotDeclaration

/**
 * Короткие FunctionGemma declarations для управления фонариком без аргументов.
 *
 * Направление действия зашито в имя функции, поэтому FunctionGemma должна
 * вернуть пустой JSON-object: `torch_on{}` или `torch_off{}`.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
interface TorchToolOneShot {

    object On : KsenaxOneShotDeclaration {
        override val codeName: String
            get() = "torch_on"
        override val description: String
            get() = "Turns on the device flashlight."
        override val parameters: String?
            get() = null
    }

    object Off : KsenaxOneShotDeclaration {
        override val codeName: String
            get() = "torch_off"
        override val description: String
            get() = "Turns off the device flashlight."
        override val parameters: String?
            get() = null
    }

    object Toggle : KsenaxOneShotDeclaration {
        override val codeName: String
            get() = "torch_toggle"
        override val description: String
            get() = "Toggles the device flashlight. Use when the user only says 'фонарик' without on/off direction."
        override val parameters: String?
            get() = null
    }
}
