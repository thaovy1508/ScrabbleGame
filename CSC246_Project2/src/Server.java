
/**
 * This program resembles the scrabble game. It's a multi-threaded TCP server in Java, with synchronization, 
 * challenge-response authentication using RSA and a using session key for efficient communication after 
 * initial authentication. The server will let users query what score a given 
 * word is worth in the game of scrabble. Each user will be able to post a word. The server will remember the most recent
 * word posted by each user and will be able to report all the users’ most recent words, sorted by their
 * scrabble score. A word can consist of up to 24 letters. Either capital or lower-case is fine, but a word can only contain
 * letters (no spaces, punctuation, digits, etc).
 * compile : javac Server.java
 * 
 * @author David Strugill, Vy Nguyen (tnguye28)
 */

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Formatter;
import java.util.Random;
import java.util.Scanner;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * A server that keeps up with a public key for every user, along with a board
 * for placing letters, like scrabble.
 */
public class Server {
	/** Port number used by the server */
	public static final int PORT_NUMBER = 26100;
	/** Table of scores for each letter */
	static int VALUES[] = { 1, 3, 3, 2, // A,B,C,D
			1, 4, 2, 4, // E,F,G,H
			1, 8, 5, 1, // I,J,K,L
			3, 1, 1, 3, // M,N,O,P
			10, 1, 1, 1, // Q,R,S,T
			1, 4, 4, 8, // U,V,W,X
			4, 10 }; // Y,Z

	/** Record for an individual user. */
	private static class UserRec implements Comparable<UserRec> {
		// Name of this user.
		String name;

		// This user's public key.
		PublicKey publicKey;

		// This user's submitted word score
		int score;

		// This user's submitted word
		String word;

		// determine if the user has at least submit once
		boolean inGame = false;
		
		// Used for sorting in ascending order of
		// word score
		/**
		 * Compares two word's scores in ascending order
		 * 
		 * @param obj the user's score to be compared to
		 * @return positive number if this user scores higher, negative number
		 *         otherwise, and 0 if they score the same value
		 */
		public int compareTo(UserRec obj) {
			return this.score - obj.score;
		}
	}

	/** List of all the user records. All threads can access */
	private static ArrayList<UserRec> userList = new ArrayList<UserRec>();

	/** List of all the user records playing scrabble. All threads can access */
	private static ArrayList<UserRec> list = new ArrayList<UserRec>();
	/** Maximum username length. */
	public static int NAME_MAX = 8;

	/** Maximum length of a word from a user. */
	public static int WORD_MAX = 24;

	/**
	 * A subclass thread to tell the thread what to do Each thread will have its own
	 * sock descriptor to connect to the client
	 * 
	 * @author Vy Nguyen
	 *
	 */
	private static class ServiceThread extends Thread {
		// the pointer to the sock descriptor for each thread
		private Socket sock;

		/**
		 * Constructor method for this thread
		 * 
		 * @param socketOfServer a unique socket for this client connection
		 */
		public ServiceThread(Socket socketOfServer) {
			this.sock = socketOfServer;
		}
 
		/**
		 * Handle interaction with our client, close it when we're done.
		 */
		@Override
		public void run() {
			try {
				// Get formatted input/output streams for this thread. These can read and write
				// strings, arrays of bytes, ints, lots of things.
				DataOutputStream output = new DataOutputStream(sock.getOutputStream());
				DataInputStream input = new DataInputStream(sock.getInputStream());

				// Get the username.
				String username = input.readUTF();

				// Make a random sequence of bytes to use as a challenge string.
				Random rand = new Random();
				byte[] challenge = new byte[16];
				rand.nextBytes(challenge);

				// Make a session key for communicating over AES. We use it later, if the
				// client successfully authenticates.
				byte[] sessionKey = new byte[16];
				rand.nextBytes(sessionKey);

				// Find this user. We don't need to synchronize here, since the set of users
				// never
				// changes.
				UserRec rec = null;
				for (int i = 0; rec == null && i < userList.size(); i++)
					if (userList.get(i).name.equals(username))
						rec = userList.get(i);

				// Did we find a record for this user?
				if (rec != null) {
					// Make sure the client encrypted the challenge properly.
					Cipher RSADecrypter = Cipher.getInstance("RSA");
					RSADecrypter.init(Cipher.DECRYPT_MODE, rec.publicKey);

					Cipher RSAEncrypter = Cipher.getInstance("RSA");
					RSAEncrypter.init(Cipher.ENCRYPT_MODE, rec.publicKey);

					// Send the client the challenge.
					putMessage(output, challenge);

					// Get back the client's encrypted challenge.
					byte[] msg = getMessage((input));
					// Decrypt the client's message with our private key and send it back.
					msg = RSADecrypter.doFinal(msg);

					// Make sure the client properly encrypted the challenge.
					if (!Arrays.equals(msg, challenge)) {
						throw new IllegalArgumentException();
					} else {
						// Send the client the session key (encrypted)
						putMessage(output, RSAEncrypter.doFinal(sessionKey));

						// At this point encryption will be done using AES
						// Make a key object from session key byte array and using it as AES.
						SecretKey key = new SecretKeySpec(sessionKey, "AES");
						// Make AES cipher objects to encrypt and decrypt with
						// the session key.
						Cipher AESEncrypter = Cipher.getInstance("AES/ECB/PKCS5Padding");
						AESEncrypter.init(Cipher.ENCRYPT_MODE, key);
						// Make a cipher object that can decrypt with this key.
						Cipher AESDecrypter = Cipher.getInstance("AES/ECB/PKCS5Padding");
						AESDecrypter.init(Cipher.DECRYPT_MODE, key);

						// Get the first client command
						msg = getMessage(input);
						String request = new String(AESDecrypter.doFinal(msg));
						Scanner sc = null;
						// All requests start with a verb.
						while (!request.equals("quit")) {
							StringBuilder reply = new StringBuilder();
							String word;
							sc = new Scanner(request);
							request = sc.next();
							if (request.equals("query") | request.equals("submit")) {
								if (sc.hasNext()) {
									word = sc.next();
									int score;
									if (sc.hasNext() || word.length() > WORD_MAX) {
										reply.append("Invalid command\n");
									} else if ((score = checkString(word)) != 0) {
										if (request.equals("query")) {
											reply.append(score + "\n");
										} else {
											synchronized (this) {
												rec.score = score;
												rec.word = word;
												if (!rec.inGame) {
													list.add(rec);
													rec.inGame = true;
												}
											}
										}
									} else {
										reply.append("Invalid command\n");
									}
								} else {
									reply.append("Invalid command\n");
								}
							} else if (request.equals("report")) {
								if (sc.hasNext()) {
									reply.append("Invalid command\n");
								} else {
									synchronized (this) {
										Collections.sort(list);
										Formatter fmt = new Formatter(reply);
										for (UserRec u : list) {
											fmt.format("%8s %24s %3d\n", u.name, u.word, u.score);
										}
									}
								}
							} else {
								reply.append("Invalid command\n");
							}
							// Send the reply back to our client.
							putMessage(output, AESEncrypter.doFinal(reply.toString().getBytes()));
							// Get the next command.
							request = new String(AESDecrypter.doFinal(getMessage(input)));
						}
						if (sc != null) {
							sc.close();
						}
					}
				}
			} catch (IOException e) {
				System.out.println("IO Error: " + e);
			} catch (GeneralSecurityException e) {
				System.err.println("Encryption error: " + e);
			} finally {
				try {
					// Close the socket on the way out.
					sock.close();
				} catch (Exception e) {
				}
			}
		}

	}

	/** Read the list of all users and their public keys. */
	private void readUsers() throws Exception {
		Scanner input = new Scanner(new File("passwd.txt"));
		while (input.hasNext()) {
			// Create a record for the next user.
			UserRec rec = new UserRec();
			rec.name = input.next();

			// Get the key as a string of hex digits and turn it into a byte array.
			String base64Key = input.nextLine().trim();
			byte[] rawKey = Base64.getDecoder().decode(base64Key);

			// Make a key specification based on this key.
			X509EncodedKeySpec pubKeySpec = new X509EncodedKeySpec(rawKey);

			// Make an RSA key based on this specification
			KeyFactory keyFactory = KeyFactory.getInstance("RSA");
			rec.publicKey = keyFactory.generatePublic(pubKeySpec);

			// Add this user to the list of all users.
			userList.add(rec);
		}
	}

	/**
	 * Utility function to read a length then a byte array from the given stream.
	 * TCP doesn't respect message boundaries, but this is essentially a technique
	 * for marking the start and end of each message in the byte stream. As a
	 * public, static method, this can also be used by the client.
	 */
	public static byte[] getMessage(DataInputStream input) throws IOException {
		int len = input.readInt();
		byte[] msg = new byte[len];
		input.readFully(msg);
		return msg;
	}

	/** Function analogous to the previous one, for sending messages. */
	public static void putMessage(DataOutputStream output, byte[] msg) throws IOException {
		// Write the length of the given message, followed by its contents.
		output.writeInt(msg.length);
		output.write(msg, 0, msg.length);
		output.flush();
	}

	/**
	 * Check if the given word contains only alphabet character If so, returns the
	 * scrabble values based on the given word
	 * 
	 * @param str the user input value
	 * @return the scrabble score if the given word is valid, 0 otherwise
	 */
	private static int checkString(String str) {
		if ((!str.equals("")) && (str != null) && (str.matches("^[a-zA-Z]*$"))) {
			int sum = 0;
			int len = str.length();
			char arr[] = str.toUpperCase().toCharArray();
			for (int i = 0; i < len; i++) {
				sum += VALUES[arr[i] - 65];
			}
			return sum;
		}
		return 0;
	}

	/**
	 * Essentially, the main method for our server, as an instance method so we can
	 * access non-static fields.
	 */
	@SuppressWarnings("resource")
	private void run(String[] args) {
		ServerSocket serverSocket = null;

		// One-time setup.
		try {
			// Read the map and the public keys for all the users.
			readUsers();

			// Open a socket for listening.
			serverSocket = new ServerSocket(PORT_NUMBER);
		} catch (Exception e) {
			System.err.println("Can't initialize server: " + e);
			e.printStackTrace();
			System.exit(1);
		}

		// Keep trying to accept new connections and serve them.
		while (true) {
			try {
				// Try to get a new client connection.
				Socket sock = serverSocket.accept();
				// create a thread
				// Handle interaction with this client.
				new ServiceThread(sock).start();
			} catch (IOException e) {
				System.err.println("Failure accepting client " + e);
			}
		}
	}

	/**
	 * Entry point for the program, set up the socket then wait to connect the
	 * client. Each client will have its own thread to execute
	 */
	public static void main(String[] args) {
		// Make a server object, so we can have non-static fields.
		Server server = new Server();
		server.run(args);
	}
}
