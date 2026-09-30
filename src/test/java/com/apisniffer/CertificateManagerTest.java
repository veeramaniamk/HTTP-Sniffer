package com.apisniffer;

import com.apisniffer.cert.CertificateManager;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.security.cert.X509Certificate;

import static org.junit.jupiter.api.Assertions.*;

public class CertificateManagerTest {

    @TempDir
    Path tempDir;

    private File certsDir;

    @BeforeEach
    void setUp() {
        certsDir = tempDir.resolve("test_certs").toFile();
    }

    @Test
    void testGenerateNewCaAndReuse() throws Exception {
        CertificateManager manager = new CertificateManager(certsDir);
        manager.initialize();

        assertTrue(manager.isNewlyGenerated());
        assertTrue(manager.getCaCertPemFile().exists());
        assertTrue(manager.getCaKeyPemFile().exists());
        assertTrue(manager.getCaCrtFile().exists());
        assertTrue(manager.getCaCerFile().exists());

        X509Certificate cert = manager.getCaCertificate();
        assertNotNull(cert);
        assertTrue(cert.getSubjectDN().getName().contains("ApiSniffer Root CA"));

        // Verify Basic Constraints has CA = true
        byte[] basicConstraintsBytes = cert.getExtensionValue(Extension.basicConstraints.getId());
        assertNotNull(basicConstraintsBytes);
        int pathLen = cert.getBasicConstraints();
        assertTrue(pathLen >= 0 || pathLen == Integer.MAX_VALUE);

        // Re-initialize: should reuse existing files
        CertificateManager reloaded = new CertificateManager(certsDir);
        reloaded.initialize();

        assertFalse(reloaded.isNewlyGenerated());
        assertEquals(cert.getSerialNumber(), reloaded.getCaCertificate().getSerialNumber());
        assertEquals(cert.getPublicKey(), reloaded.getCaCertificate().getPublicKey());
    }
}
