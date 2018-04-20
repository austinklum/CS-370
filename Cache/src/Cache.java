import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

public class Cache {
	CacheBlock[][] cache;
	CacheType cacheType;
	Stats stats;
	int offsetNum;
	int indexNum;
	int sets;
	int ways;
	int[] useNext;
	AllocPolicy allocPolicy;
	WritePolicy writePolicy;
	
	private enum CacheType {
		DIRECT_MAPPED,
		SET_ASSOCIATIVE;
	}
	
	private enum AllocPolicy {
		WRITE_ALLOCATE, 
		WRITE_NO_ALLOCATE;
	}
	
	private enum WritePolicy {
		WRITE_THROUGH, 
		WRITE_BACK;
	}
	
	private enum ReadWrite {
		READ, 
		WRITE;
	}
	
	
	private class CacheBlock {
		boolean isValid, isDirty;
		int tag;
		
		private CacheBlock(){
			isValid = false;
			isDirty = false;
		}
	}
	
	private class Stats {
		int rHits,wHits,rMisses,wMisses,wb,wt;
		private Stats() {
			rHits = wHits = rMisses = wMisses = wb = wt = 0;
		}
		/**
		 * Prints out the statistics of the cache 
		 */
		@Override
		public String toString() {
			double hrate =  ((double)(rHits + wHits) / (double)(rHits + wHits + rMisses + wMisses));
			return  "rhits: " + rHits + "\n" +
					"whits: " + wHits + "\n" +
					"rmisses: " + rMisses + "\n" +
					"wmisses: " + wMisses + "\n" +
					"hrate: " + hrate + "\n" +
					"wb: " + wb + "\n" +
					"wt: " + wt;
		}
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
		
		int cacheType = scan.nextInt();
		offsetNum = scan.nextInt();
		indexNum = scan.nextInt();
		String allocPol = scan.next();
		String writePol = scan.next();
		
		setCacheType(cacheType);
		setAllocPolicy(allocPol);
		setWritePolicy(writePol);
		
		sets = 2 << indexNum-1;
		cache = new CacheBlock[sets][ways];
		for(int i = 0; i < cache.length; i++) {
			for (int j = 0; j < cache[i].length; j++) {
				cache[i][j] = new CacheBlock();
				//System.out.println("Pause");
			}
		}
		stats = new Stats();
		useNext = new int[sets];
	}

	/**
	 * @param type
	 */
	private void setCacheType(int type) {
		switch(type) {
		case 1:
			cacheType = Cache.CacheType.DIRECT_MAPPED;
			ways = 1;
			break;
		case 2:
			cacheType = Cache.CacheType.SET_ASSOCIATIVE;
			ways = 2;
			break;
		}
	}

	/**
	 * @param writePol
	 */
	private void setWritePolicy(String writePol) {
		switch(writePol) {
		case "wt":
			writePolicy = Cache.WritePolicy.WRITE_THROUGH;
			break;
		case "wb":
			writePolicy = Cache.WritePolicy.WRITE_BACK;
			break;
		}
	}

	/**
	 * @param allocPol
	 */
	private void setAllocPolicy(String allocPol) {
		switch(allocPol) {
		case "wa":
			allocPolicy = Cache.AllocPolicy.WRITE_ALLOCATE;
			break;
		case "wna":
			allocPolicy = Cache.AllocPolicy.WRITE_NO_ALLOCATE;
			break;
		}
	}
	
	/**
	 * Returns the value range of start and end in an integer using a bitmask built up from inputs.
	 *  WARNING! - Assumes the 0th start position is on the far right. ie. Read from right to left.
	 *  
	 * @param start starting index of bits
	 * @param end ending indexing of bits 
	 * @param addr address to get bits from
	 * @return subset of integer in the given range
	 * */
	private int bitsAt(int start, int end, long addr){
		   int bitMask = 0;
		   for (int i=start; i<=end; i++) {
			   bitMask |= 1 << i;
		   }
		   return ((int)(addr & bitMask)) >>> start;
		}
	
	/**
	 * Reads the access file and keeps track of cache
	 */
	private void processAccesses() {
		Scanner scan = null;
		try {
			scan = new Scanner(new File("accesses.txt"));
		} catch (FileNotFoundException e) {
			System.out.println("File not found!");
		}
		while(scan.hasNext()) {
			//Get one line of input at a time
			ReadWrite rw = setReadWrite(scan.next());
			long addr = scan.nextLong(16);
			int index = bitsAt(offsetNum,offsetNum+indexNum-1,addr);
			int tag = bitsAt(offsetNum+indexNum,31,addr);
			//We missed and need to allocate/replace
			if(!checkHit(rw, index, tag) && (rw == ReadWrite.READ || allocPolicy == AllocPolicy.WRITE_ALLOCATE)) {
				allocate(rw,index,tag);
			}
			
		}
	}

	private void allocate(ReadWrite rw, int index, int tag) {
		CacheBlock[] block = cache[index];
		
		//Easy case: one of the blocks are not valid.
		if(!block[0].isValid) {
			block[0].isValid = true;
			block[0].tag = tag;
			//cache[index][0] = block[0];
			useNext[index] = 1;
			return;
		} else if (cacheType == CacheType.SET_ASSOCIATIVE && !block[1].isValid) {
			block[1].isValid = true;
			block[1].tag = tag;
			//cache[index][1] = block[1];
			useNext[index] = 0;
			return;
		}
		
		if (cacheType == CacheType.DIRECT_MAPPED) {
			block[0].tag = tag;
			if (writePolicy == WritePolicy.WRITE_BACK && block[0].isDirty) {
				stats.wb++;
				// Still dirty if a write, clean when a read.
				block[0].isDirty = rw == ReadWrite.WRITE;
			}
			//cache[index][0] = block[0];
		} else if (cacheType == CacheType.SET_ASSOCIATIVE) {
			int nextIndex = useNext[index];
			block[nextIndex].tag = tag;
			
			if(nextIndex == 0) {
				useNext[index] = 1;
			} else if (nextIndex == 1) {
				useNext[index] = 0;
			}
			
			if(writePolicy == WritePolicy.WRITE_BACK && block[nextIndex].isDirty) {
				stats.wb++;
				block[nextIndex].isDirty = rw == ReadWrite.WRITE;
			}
			//cache[index][nextIndex] = block[nextIndex];
		}
		
	}

	/**
	 * Checks hit of cache. Add to hit or miss counter.
	 * Returns true if hit, and false if miss.
	 * @param rw
	 * @param index
	 * @param tag
	 * @return 
	 */
	private boolean checkHit(ReadWrite rw, int index, int tag) {
		boolean isHit = isHit(index,tag);
		if(isHit) {
			//Hit on read or write
			if(rw == ReadWrite.READ) {
				stats.rHits++;
			} else if (rw == ReadWrite.WRITE) {
				stats.wHits++;
			}
		} else {
			//Miss on read or write
			if(rw == ReadWrite.READ) {
				stats.rMisses++;
			} else if (rw == ReadWrite.WRITE) {
				stats.wMisses++;
				//When a write back cache and write no-allocate and write miss, increment write through
				if(writePolicy == WritePolicy.WRITE_BACK && allocPolicy == AllocPolicy.WRITE_NO_ALLOCATE) {
					stats.wt++;
				}
			}
		}
		//Regardless of hit or miss, when a write through cache and a write action, increment write through.
		if(writePolicy == WritePolicy.WRITE_THROUGH && rw == ReadWrite.WRITE) {
			stats.wt++;
		}
		return isHit;
	}
	
	private boolean isHit(int index, int tag) {
		boolean isHit = false;
		//Index into cacheblock
		CacheBlock[] block = cache[index];
		
		//check block for hit
		if (block[0].isValid && block[0].tag == tag) {
			isHit = true;
		}
		//Check the other block for a hit if we miss on block 0 and is set associative.
		if(!isHit && cacheType == CacheType.SET_ASSOCIATIVE && block[1].isValid && block[1].tag == tag) {
			isHit = true;
		}
		return isHit;
	}

	private ReadWrite setReadWrite(String rw) {
		switch(rw) {
		case "r":
			return ReadWrite.READ;
		case "w":
			return ReadWrite.WRITE;
		default:
			System.out.println("Invalid read write set!");
			return null;
		}
	}

	/**
	 * Prints out the statistics of the cache 
	 */
	@Override
	public String toString() {
		return stats.toString();
	}
	
	public static void main(String[] args) {
		Cache cache = new Cache();
		cache.processAccesses();
		System.out.println(cache);
	}

}