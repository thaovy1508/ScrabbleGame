import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Scanner;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * Client supporting simple interaction with the server. This class represents
 * the client side. At startup, the client reads the user’s username and sends
 * it to the server. If the client types in an invalid user name (e.g., one
 * that’s too long), the client prints “Invalid username” and terminates. If the
 * username is valid, the client sends it to the server and waits for a
 * challenge. If the client types in a username that doesn’t have a file with a
 * private key, or if the client fails to authenticate with the server, it just
 * throws an exception and terminates After authenticating, the user will be
 * prompted for commands. Some valid commands are: quit, submit word, query
 * word, report.
 * 
 * @author David Strugill, Vy Nguyen (tnguye28)
 * run : java Client localhost
 */
public class Client {
	/**
	 * Entry point for the program, set up the socket to the server.
	 */
	public static void main(String[] args) {
		// Complain if we don't get the right number of arguments.
		if (args.length != 1) {
			System.out.println("Usage: Client <host>");
			System.exit(-1);
		}

		try {
			// Try to create a socket connection to the server.
			Socket sock = new Socket(args[0], Server.PORT_NUMBER);

			// Get formatted input/output streams for talking with the server.
			DataInputStream input = new DataInputStream(sock.getInputStream());
			DataOutputStream output = new DataOutputStream(sock.getOutputStream());

			// Get a username from the user and send it to the server.
			Scanner scanner = new Scanner(System.in);
			System.out.print("username> ");
			String name = scanner.nextLine();

			// Make sure the username is valid (not too short, too long or containing
			// spaces)
			if (name.length() == 0 || name.length() > Server.NAME_MAX || name.matches(".*\\s.*")) {
				System.out.println("Invalid username");
				System.exit(1);
			}

			// Try to read the user's private key.
			Scanner keyScanner = new Scanner(new File(name + ".txt"));
			String base64Key = keyScanner.nextLine();
			byte[] rawKey = Base64.getDecoder().decode(base64Key);
			keyScanner.close();

			// Send username to the server.
			output.writeUTF(name);
			output.flush();

			// Get the challenge string (really a byte array) from the server.
			byte[] challenge = Server.getMessage(input);

			// Make a key specification based on this key.
			PKCS8EncodedKeySpec privKeySpec = new PKCS8EncodedKeySpec(rawKey);

			// Get an RSA key based on this specification
			KeyFactory keyFactory = KeyFactory.getInstance("RSA");
			PrivateKey privateKey = keyFactory.generatePrivate(privKeySpec);

			// Make a cipher object that will encrypt using this key.
			Cipher RSAEncrypter = Cipher.getInstance("RSA");
			RSAEncrypter.init(Cipher.ENCRYPT_MODE, privateKey);

			// Make another cipher object that will decrypt using this key.
			Cipher RSADecrypter = Cipher.getInstance("RSA");
			RSADecrypter.init(Cipher.DECRYPT_MODE, privateKey);

			// Encrypt the challenge with our private key and send it back.
			byte[] rawCiphertext = RSAEncrypter.doFinal(challenge);
			Server.putMessage(output, rawCiphertext);

			// Get the symmetric key (session key) from the server and make AES
			// encrypt/decrypt objects for it.
			byte[] sessionKey = Server.getMessage(input);
			sessionKey = RSADecrypter.doFinal(sessionKey);
			// Make a key object from this byte array.
			SecretKey key = new SecretKeySpec(sessionKey, "AES");

			// Make a cipher object that can encrypt with this session key.
			Cipher AESEncrypter = Cipher.getInstance("AES/ECB/PKCS5Padding");
			AESEncrypter.init(Cipher.ENCRYPT_MODE, key);
			// Make a cipher object that can decrypt with this key.
			Cipher AESDecrypter = Cipher.getInstance("AES/ECB/PKCS5Padding");
			AESDecrypter.init(Cipher.DECRYPT_MODE, key);

			// Read commands from the user and print server responses.
			String request = "";
			System.out.print("cmd> ");
			while (scanner.hasNextLine() && !(request = scanner.nextLine()).equals("quit")) {
				Server.putMessage(output, AESEncrypter.doFinal(request.getBytes()));

				// Read and print the response.
				String response = new String(AESDecrypter.doFinal(Server.getMessage(input)));
				System.out.print(response);

				System.out.print("cmd> ");
			}

			// Send the exit command to the server.
			Server.putMessage(output, AESEncrypter.doFinal(request.getBytes()));
			// We are done communicating with the server.
			sock.close();
			scanner.close();
		} catch (IOException e) {
			System.err.println("IO Error: " + e);
		} catch (GeneralSecurityException e) {
			System.err.println("Encryption error: " + e);
		}
	}
}
