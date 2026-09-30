package com.apisniffer.cert;

import com.browserup.bup.mitm.CertificateAndKey;
import com.browserup.bup.mitm.CertificateAndKeySource;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.*;
import java.math.BigInteger;
import java.nio.file.Files;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.Date;

/**
 * Manages the self-signed Root Certificate Authority (CA) used for TLS MITM interception.
 * Generates a new Root CA on first run or reuses the existing CA files from ./certs/.
 */
public class CertificateManager {

    static {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private static final String CA_SUBJECT = "CN=ApiSniffer Root CA, O=ApiSniffer, OU=Debugging Proxy, C=US";
    private static final long TEN_YEARS_MS = 10L * 365 * 24 * 60 * 60 * 1000L;
    private static final long ONE_DAY_MS = 24L * 60 * 60 * 1000L;

    private final File certsDir;
    private final File caCertPemFile;
    private final File caKeyPemFile;
    private final File caCrtFile;
    private final File caCerFile;

    private X509Certificate caCertificate;
    private PrivateKey caPrivateKey;
    private boolean newlyGenerated;

    public CertificateManager() {
        this(new File("./certs"));
    }

    public CertificateManager(File certsDir) {
        this.certsDir = certsDir;
        this.caCertPemFile = new File(certsDir, "ca.pem");
        this.caKeyPemFile = new File(certsDir, "ca-key.pem");
        this.caCrtFile = new File(certsDir, "ca.crt");
        this.caCerFile = new File(certsDir, "ca.cer");
    }

    /**
     * Initializes the Root CA: reuses existing certificate if present, or generates a new one.
     */
    public synchronized void initialize() throws Exception {
        if (caCertPemFile.exists() && caKeyPemFile.exists()) {
            try {
                loadExistingCa();
                this.newlyGenerated = false;
                return;
            } catch (Exception e) {
                System.err.println("[WARN] Failed to load existing Root CA from " + caCertPemFile + ": " + e.getMessage());
                System.err.println("[WARN] Regenerating fresh Root CA certificate...");
            }
        }

        generateNewCa();
        this.newlyGenerated = true;
    }

    private void loadExistingCa() throws Exception {
        // Load certificate
        try (Reader reader = new FileReader(caCertPemFile);
             PEMParser pemParser = new PEMParser(reader)) {
            Object obj = pemParser.readObject();
            if (obj instanceof X509CertificateHolder holder) {
                this.caCertificate = new JcaX509CertificateConverter()
                        .setProvider("BC")
                        .getCertificate(holder);
            } else {
                throw new IllegalStateException("Unexpected certificate format in " + caCertPemFile + ": " + obj);
            }
        }

        // Load private key
        try (Reader reader = new FileReader(caKeyPemFile);
             PEMParser pemParser = new PEMParser(reader)) {
            Object obj = pemParser.readObject();
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");
            if (obj instanceof PEMKeyPair keyPair) {
                this.caPrivateKey = converter.getPrivateKey(keyPair.getPrivateKeyInfo());
            } else if (obj instanceof org.bouncycastle.asn1.pkcs.PrivateKeyInfo pki) {
                this.caPrivateKey = converter.getPrivateKey(pki);
            } else {
                throw new IllegalStateException("Unexpected private key format in " + caKeyPemFile + ": " + obj);
            }
        }
    }

    private void generateNewCa() throws Exception {
        if (!certsDir.exists() && !certsDir.mkdirs()) {
            throw new IOException("Failed to create certificate directory: " + certsDir.getAbsolutePath());
        }

        // 1. Generate RSA 2048-bit Key Pair
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048, new SecureRandom());
        KeyPair keyPair = keyGen.generateKeyPair();
        this.caPrivateKey = keyPair.getPrivate();

        // 2. Generate Self-Signed X.509 Root CA Certificate
        X500Name issuer = new X500Name(CA_SUBJECT);
        BigInteger serial = BigInteger.valueOf(System.currentTimeMillis());
        Date notBefore = new Date(System.currentTimeMillis() - ONE_DAY_MS);
        Date notAfter = new Date(System.currentTimeMillis() + TEN_YEARS_MS);

        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                issuer,
                serial,
                notBefore,
                notAfter,
                issuer,
                keyPair.getPublic()
        );

        JcaX509ExtensionUtils extUtils = new JcaX509ExtensionUtils();
        // Basic Constraints: isCA = true
        certBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        // Key Usage: keyCertSign, cRLSign, digitalSignature
        certBuilder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign | KeyUsage.digitalSignature));
        // Subject Key Identifier
        certBuilder.addExtension(Extension.subjectKeyIdentifier, false, extUtils.createSubjectKeyIdentifier(keyPair.getPublic()));
        // Authority Key Identifier
        certBuilder.addExtension(Extension.authorityKeyIdentifier, false, extUtils.createAuthorityKeyIdentifier(keyPair.getPublic()));

        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
                .setProvider("BC")
                .build(keyPair.getPrivate());

        this.caCertificate = new JcaX509CertificateConverter()
                .setProvider("BC")
                .getCertificate(certBuilder.build(signer));

        // 3. Save to ./certs/ca.pem
        try (JcaPEMWriter pemWriter = new JcaPEMWriter(new FileWriter(caCertPemFile))) {
            pemWriter.writeObject(caCertificate);
        }

        // 4. Save to ./certs/ca-key.pem
        try (JcaPEMWriter pemWriter = new JcaPEMWriter(new FileWriter(caKeyPemFile))) {
            pemWriter.writeObject(caPrivateKey);
        }

        // 5. Also write ca.crt (PEM format) and ca.cer (DER format) for Android compatibility
        try (JcaPEMWriter pemWriter = new JcaPEMWriter(new FileWriter(caCrtFile))) {
            pemWriter.writeObject(caCertificate);
        }
        Files.write(caCerFile.toPath(), caCertificate.getEncoded());
    }

    public CertificateAndKeySource getCertificateAndKeySource() {
        if (caCertificate == null || caPrivateKey == null) {
            throw new IllegalStateException("CertificateManager has not been initialized!");
        }
        return () -> new CertificateAndKey(caCertificate, caPrivateKey);
    }

    public X509Certificate getCaCertificate() {
        return caCertificate;
    }

    public PrivateKey getCaPrivateKey() {
        return caPrivateKey;
    }

    public File getCaCertPemFile() {
        return caCertPemFile;
    }

    public File getCaKeyPemFile() {
        return caKeyPemFile;
    }

    public File getCaCrtFile() {
        return caCrtFile;
    }

    public File getCaCerFile() {
        return caCerFile;
    }

    public boolean isNewlyGenerated() {
        return newlyGenerated;
    }
}
