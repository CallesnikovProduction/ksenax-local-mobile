package com.kolesnikovprod.ksetaorch.communication.tools.policy

/**
 * Runtime-контекст проверки tool call-а.
 *
 * Context приходит снаружи orchestration-слоя: обычно его собирает ViewModel
 * или application use-case перед вызовом coordinator-а. Здесь фиксируется не
 * то, что сказала модель, а текущее состояние приложения: подтверждал ли
 * пользователь рискованное действие, какие namespaces или tool names временно
 * заблокированы.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
data class KsenaxPolicyContext(
    /**
     * Пользователь подтвердил действие, для которого policy потребовала
     * подтверждение.
     *
     * Это не только HIGH risk. Подтверждение может потребоваться из-за
     * definition.requiresConfirmationByDefault или из-за конкретного tool call-а,
     * который пришёл от router-а с requires_confirmation=true.
     *
     * @since 0.2
     */
    val userConfirmedRequiredActions: Boolean     = false,

    /**
     * Namespaces, которые нельзя исполнять в текущем turn-е.
     *
     * @since 0.2
     */
    val blockedNamespaces:            Set<String> = emptySet(),

    /**
     * Конкретные tool names, которые нельзя исполнять в текущем turn-е.
     *
     * @since 0.2
     */
    val blockedToolNames:             Set<String> = emptySet(),
)
