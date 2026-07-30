package dev.openksenax.addons.contract.model;

import dev.openksenax.addons.contract.model.ModelGenerationResult;

/** One-shot model provider callback. @since 0.3 */
oneway interface IModelGenerationCallback {

    void onCompleted(
        in ModelGenerationResult result
    );
}
