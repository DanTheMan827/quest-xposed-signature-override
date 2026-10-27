package com.example.signinginfooverride;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

/** Runs in the unhooked companion process so destination queries use authentic package data. */
final class CertificateResolver implements CertificateLookup.Backend {
    private final Context context;
    CertificateResolver(Context context) { this.context = context; }

    static CertificateLookup.Result resolve(Context context, String source, long version, String target) throws Exception {
        return CertificateLookup.resolve(new CertificateResolver(context), source, version, target);
    }

    @Override public Long installedVersion(String packageName) throws Exception {
        PackageInfo info = installed(packageName, 0);
        return info == null ? null : info.getLongVersionCode();
    }

    @Override public byte[] savedPem(String packageName, long version) throws Exception {
        if (!StorageAccess.isGranted(context)) return null;
        File pem = OverrideFiles.resolve(new File(BuildConfig.OVERRIDE_DIRECTORY), OverrideFiles.filename(packageName, version));
        return pem.isFile() ? OverrideFiles.read(pem) : null;
    }

    @Override public byte[] installedPem(String packageName) throws Exception {
        PackageInfo info = installed(packageName, PackageManager.GET_SIGNING_CERTIFICATES);
        if (info == null || info.signingInfo == null) return null;
        Signature[] signers = info.signingInfo.getApkContentsSigners();
        if (signers == null || signers.length == 0) return null;
        List<X509Certificate> certificates = new ArrayList<>();
        for (Signature signer : signers) certificates.add((X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(signer.toByteArray())));
        return OverrideFiles.pem(certificates);
    }

    private PackageInfo installed(String packageName, int flags) throws Exception {
        try { return context.getPackageManager().getPackageInfo(packageName, flags); }
        catch (PackageManager.NameNotFoundException absent) { return null; }
    }
}
