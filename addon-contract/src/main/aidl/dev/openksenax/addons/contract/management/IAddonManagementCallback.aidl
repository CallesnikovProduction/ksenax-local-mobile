package dev.openksenax.addons.contract.management;

import dev.openksenax.addons.contract.management.AddonManagementResponse;

/** One-shot asynchronous management callback. @since 0.3 */
oneway interface IAddonManagementCallback {

    /** Delivers the final response to OpenKsenax. @since 0.3 */
    void onResult(in AddonManagementResponse response);
}
