import java.util.ArrayList;
import java.util.Scanner;

/**
 * This program reads in a list of integer values. In the list, it will check
 * each value to see whether it’s prime or composite. It will report how many
 * prime values are in the input and it will (optionally) report each prime
 * value as it detects it. The user will decide the number of threads for the
 * program to run.
 * 
 * @author Vy Nguyen (tnguye28)
 *
 */
public class Prime {
	/** list of values */
	static ArrayList<Integer> vList = new ArrayList<Integer>();
	/** number of values in the list */
	static int vCount = 0;
	/** number of threads */
	static int threads = 0;
	/** the flag to determine if the prime values need to be reported */
	static boolean report = false;

	/**
	 * The subclass of Thread that its functionality is to detect prime number and
	 * hold a variable to determine the total number of prime value
	 * 
	 * @author Vy Nguyen
	 */
	private static class MyThread extends Thread {
		/** a field to store prime numbers */
		private int count = 0;
		/** the list of values that this thread will check for primeess */
		private int[] values;

		/**
		 * Constructor method for MyThread
		 * 
		 * @param nums  the number of values that this thread will check for primeness
		 * @param start the initial index of the list
		 */
		public MyThread(int nums, int start) {
			values = new int[nums];
			for (int i = 0; i < nums; i++) {
				values[i] = (int) vList.get(start + i * threads);
			}
		}

		public void run() {
			int nums = values.length;
			for (int i = 0; i < nums; i++) {
				int value = values[i];
				boolean found = false;
				if (value <= 1) {
					continue;
				} else if (value <= 3) {
					found = true;
				} else if (value % 2 == 0 || value % 3 == 0) {
					continue;
				} else {
					found = true;
					for (int j = 5; j * j <= value; j += 6) {
						if (value % j == 0 || value % (j + 2) == 0)
							found = false;
					}
				}
				if (found) {
					count++;
					if (report)
						System.out.println(value);
				}
			}
		}

		/**
		 * Returns the total prime number that the thread has detected.
		 * 
		 * @return the total prime number from this thread
		 */
		public int getPrimeCount() {
			return count;
		}

	}

	/**
	 * Starting point of the program. Creates threads and waits for them to finish
	 * the execution
	 * 
	 * @param argv command line arguments
	 */
	public static void main(String[] argv) {
		int length = argv.length;
		if (length < 1 || length > 2) {
			usage();
		}
		try {
			threads = Integer.parseInt(argv[0]);
		} catch (NumberFormatException e) {
			usage();
		}
		if (threads < 1) {
			fail("Number of threads must be a positive number.");
		}
		if (length == 2) {
			if (argv[1].equals("report")) {
				report = true;
			}
		}
		readList();
		int split = vCount / threads;
		int remain = vCount % threads;
		MyThread[] myThreads = new MyThread[threads];
		for (int i = 0; i < threads; i++) {
			int nums = split;
			if (remain > 0) {
				nums++;
				remain--;
			}
			myThreads[i] = new MyThread(nums, i);
			myThreads[i].start();
		}
		int totalCount = 0;
		for (int i = 0; i < threads; i++) {
			try {
				myThreads[i].join();
				totalCount += myThreads[i].getPrimeCount();
			} catch (InterruptedException e) {
				fail("Interrupted during join!");
			}
		}
		System.out.println("Prime count: " + totalCount);
		System.exit(0);
	}

	/**
	 * This method reads in the list of values to test for primeness from the
	 * standard input
	 */
	private static void readList() {
		Scanner sc = new Scanner(System.in);
		while (sc.hasNextInt()) {
			vList.add(sc.nextInt());
			vCount++;
		}
		sc.close();
	}

	/**
	 * This static method prints out the usage message and exits the program
	 * unsuccessfully
	 */
	private static void usage() {
		System.out.println("usage: Prime <threads>");
		System.out.println("       Prime <threads> report");
		System.exit(1);
	}

	/**
	 * This static method prints out an error message and exit.
	 * 
	 * @param msg error message to print out
	 */
	private static void fail(String msg) {
		System.out.println(msg);
		System.exit(1);
	}
}
