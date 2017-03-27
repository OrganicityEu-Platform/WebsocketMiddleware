package dk.alexandra.organicity.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;

/**
 * This class parses a JSON Web Tokens (JWT) and verfies it agains the Organicity.
 * You can use this class also to use any other certificate by proding it
 * 
 * @author Dennis Boldt
 *
 */
public class JwtParser {

	InputStream is;
	
	/**
	 * Using this constructor, the default accounts.organicity.eu certificate is used
	 * @see: https://stackoverflow.com/questions/2653322/getresourceasstream-not-loading-resource-in-webapp
	 */
	public JwtParser() {
		// This is needed within the Wildfly
		this.is = Thread.currentThread().getContextClassLoader().getResourceAsStream("/accounts.organicity.eu.cert.pem");

		// This is needed within Eclipse or within a JAR
		if(this.is == null) {
			this.is = this.getClass().getResourceAsStream("/accounts.organicity.eu.cert.pem");
		}
		
		if(this.is == null) {
			System.out.println("Certificate not found!");
		}
	}

	/**
	 * Using this constructor, any other certificate can be used
	 * 
	 * @param f
	 * @throws FileNotFoundException
	 */
	public JwtParser(File f) throws FileNotFoundException {
        this.is = new FileInputStream(f);
	}
	
	/*
	 * This reads the public key from the certificate 
	 */
	private PublicKey getPublicKeyFromCert() {
		PublicKey pk = null;

		CertificateFactory f = null;
		try {
			f = CertificateFactory.getInstance("X.509");
			X509Certificate certificate = (X509Certificate) f.generateCertificate(this.is);
			pk = certificate.getPublicKey();
		} catch (CertificateException e) {
			System.err.println("CERTIFICATE NOT FOUND");
		}
		return pk;
	}

	/**
	 * 
	 * @param jwt A BASE64 encoded JWT
	 * @return A Claims object, which can be used 
	 * @throws Exception If the JWT is not valid or expired, this exception is thrown
	 */
	public Claims parseJWT(String jwt) throws Exception{
		PublicKey pk = getPublicKeyFromCert();
		return Jwts.parser().setSigningKey(pk).parseClaimsJws(jwt).getBody();
	}
}
