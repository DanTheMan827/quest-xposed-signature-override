package com.example.signinginfooverride;

public final class ContentCallPolicyTest {
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
    public static void main(String[] args) {
        assetCalls(); purchaseAndLanguageCalls(); permissionCallsExcluded(); unknownCallsExcluded(); checkoutCall();
        System.out.println("5 content call boundary checks passed");
    }
    private static void assetCalls() {
        for (String method : new String[]{"assetFileList", "assetFileStatusById", "assetFileStatusByName",
                "assetFileDownloadByIdWithCb", "assetFileDownloadByNameWithCb", "assetFileDeleteById",
                "assetFileDownloadCancelByNameWithCb"}) check(ContentCallPolicy.translate(method));
    }
    private static void purchaseAndLanguageCalls() {
        for (String method : new String[]{"getViewerPurchases", "getViewerPurchasesDurableCacheJSON",
                "languagePackGetCurrent", "languagePackSetCurrent"}) check(ContentCallPolicy.translate(method));
    }
    private static void permissionCallsExcluded() {
        for (String method : new String[]{"getSharedMicrophoneData", "getSharedMicrophoneSocketFD",
                "getPlatformIntegrityAttestationToken", "sharedMicrophoneStop", "launchOtherApp",
                "getSystemVoipData", "getAppScopedAccessToken"}) check(!ContentCallPolicy.translate(method));
    }
    private static void unknownCallsExcluded() {
        for (String method : new String[]{"assetFileFuturePrivilegeOperation", "assetFile", "", "getViewerPurchasesAndModifyRoles", null})
            check(!ContentCallPolicy.translate(method));
    }
    private static void checkoutCall() {
        check(ContentCallPolicy.translate("getIAPCheckoutFlowIntent"));
        check(!ContentCallPolicy.translate("getIAPCheckoutFlowIntentAndModifyPayment"));
        check(!ContentCallPolicy.translate("purchase"));
    }
}
