package com.kolesnikovprod.ksetaorch.communication.work.oneshot

/**
 * Function declaration для короткого FunctionGemma prompt-а.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
interface KsenaxOneShotDeclaration {
    val codeName: String
    val description: String
    val parameters: String?
}
