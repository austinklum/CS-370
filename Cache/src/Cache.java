import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

public class Cache {
	CacheBlock[][] cache;
	
	private enum AllocPolicy {
		WRITE_ALLOCATE, 
		WRITE_NO_ALLOCATE;
	}
	
	private enum WritePolicy {
		WRITE_THROUGH, 
		WRITE_BACK;
	}
	
	private class CacheBlock {
			
	}
	
	/**
	 * Reads the parameters file and populates the cache object
	 */
	private Cache() {
		Scanner scan = null;
		try {
			 scan = new Scanner(new File("parameters.txt"));
		} catch (FileNotFoundException e) {
			System.out.println("File not found!");
		}
		cache = new CacheBlock[scan.nextInt()][scan.nextInt()];
	}
	
	/**
	 * Reads the access file and keeps track of cache
	 */
	private void processAccesses() {
		
	}
	
	/**
	 * Prints out the statistics of the cache 
	 */
	@Override
	public String toString() {
		return null;
	}
	
	public static void main(String[] args) {
		Cache cache = new Cache();
		cache.processAccesses();
		System.out.println(cache);
	}

}
