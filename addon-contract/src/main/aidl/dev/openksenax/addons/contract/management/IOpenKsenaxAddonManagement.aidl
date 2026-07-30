package dev.openksenax.addons.contract.management;

import dev.openksenax.addons.contract.management.IAddonManagementCallback;

/** Asynchronous Binder API for an autonomous add-on APK. @since 0.3 */
oneway interface IOpenKsenaxAddonManagement {

    /* Requests the current runtime state. */
    void requestRuntimeStatus(
        String requestId,
        IAddonManagementCallback callback
    );

    /* Requests a runtime enabled-state change. */
    void requestSetEnabled(
        String requestId,
        boolean enabled,
        IAddonManagementCallback callback
    );

    /* Requests the add-on-owned UI entry point. */
    void requestUiEntryPoint(
        String requestId,
        IAddonManagementCallback callback
    );

    /* Best-effort cancellation of a previous request. */
    void cancel(String requestId);
}
