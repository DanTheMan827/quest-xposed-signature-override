package com.example.signinginfooverride;

/** Lazy certificate selection: an exact source override takes precedence over destination data. */
final class CertificatePriority {
    interface Candidate { byte[] load() throws Exception; }

    static byte[] resolve(Candidate exactSourceOverride, Candidate destination) throws Exception {
        byte[] certificate = exactSourceOverride.load();
        if (certificate != null) return certificate;
        certificate = destination.load();
        if (certificate == null) throw new IllegalArgumentException("No usable signing certificate for this swap");
        return certificate;
    }
}
