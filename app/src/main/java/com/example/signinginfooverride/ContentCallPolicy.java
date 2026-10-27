package com.example.signinginfooverride;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Verified package-scoped content handlers from the supplied OVRService interface. */
final class ContentCallPolicy {
    private static final Set<String> TRANSLATED = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "assetFileDeleteById", "assetFileDeleteByName", "assetFileDownloadById", "assetFileDownloadByIdWithCb",
            "assetFileDownloadByName", "assetFileDownloadByNameWithCb", "assetFileDownloadCancelById",
            "assetFileDownloadCancelByIdWithCb", "assetFileDownloadCancelByName", "assetFileDownloadCancelByNameWithCb",
            "assetFileList", "assetFileStatusById", "assetFileStatusByName", "languagePackGetCurrent", "languagePackSetCurrent",
            "getViewerPurchases", "getViewerPurchasesDurableCacheJSON", "getIAPCheckoutFlowIntent")));

    static boolean translate(String methodName) { return TRANSLATED.contains(methodName); }
}
