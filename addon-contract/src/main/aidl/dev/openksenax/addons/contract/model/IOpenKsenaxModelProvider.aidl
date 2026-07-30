package dev.openksenax.addons.contract.model;

import dev.openksenax.addons.contract.model.ModelGenerationRequest;
import dev.openksenax.addons.contract.model.IModelGenerationCallback;

/** IPC access from a trusted add-on APK to the OpenKsenax model provider. @since 0.3 */
interface IOpenKsenaxModelProvider {

    int getProviderApiVersion();

    String[] getAvailableCapabilities();

    oneway void generate(
        in ModelGenerationRequest request,
        in IModelGenerationCallback callback
    );

    oneway void cancel(String requestId);
}
