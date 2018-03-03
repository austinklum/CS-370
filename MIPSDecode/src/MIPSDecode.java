import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Scanner;

public class MIPSDecode {
	private enum Type {
		i,r,j;
	}
	private static class Instruction {
		int addr, word, op, rs, rt, rd, shamt, funct, imm, jAddr;
		Type type;
		private Instruction(int addr, int word) {
			this.addr = addr;
			this.word = word;
			
			op = bitsAt(26,31);
			type = getTypeFromOp();

			rs = bitsAt(21,25);
			rt = bitsAt(16,20);
			rd = bitsAt(11,15);
			shamt = bitsAt(6,10);
			funct = bitsAt(5,0);
			imm = bitsAt(0,15);
			jAddr = bitsAt(0,25);
			
		}
		/**
		 * Returns the value range of start and end in an integer using a bitmask built up from inputs.
		 *  WARNING! - Assumes word has been set and the 0th start position is on the far right. ie. Read from right to left.
		 *  
		 * @param start starting index of bits
		 * @param end ending indexing of bits 
		 * @return subset of integer in the given range
		 * */
		public int bitsAt(int start, int end){
			   int results = 0;
			   for (int i=start; i<=end; i++) {
			       results |= 1 << i;
			   }
			   
			   return word & results;
			}
		//PRE: assumes op has been set
		private Type getTypeFromOp() {
			switch(op) {
				case 0:
					return Type.r;
				case 0x2:
				case 0x3:
					return Type.j;
				default:
					return Type.i;
			}
		}

	}
	
	private static class Stats {
		int insts, rType, iType, jType, fwdTaken, bkwTaken, notTaken, loads, stores;
		int[][] reg;
		private Stats () {
			 insts = rType = iType = jType = fwdTaken = bkwTaken = notTaken = loads = stores = 0;
			 reg = new int[32][2];
		}
		
		private void addToType(Type type) {
			switch(type) {
				case r:
					rType++;
				case i:
					iType++;
				case j:
					jType++;
			}
		}
		
		@Override
		public String toString() {
			String str =
					"insts: " + insts + "\n" +
					"r-type: " + rType + "\n" +
					"i-type: " + iType + "\n" +
					"j-type: " + jType + "\n" +
					"fwd-taken: " + fwdTaken + "\n" +
					"bkw-taken: " + bkwTaken + "\n" +
					"not-taken: " + notTaken + "\n" +
					"loads: " + loads + "\n" +
					"stores: " + stores + "\n";
			//Loop through all the registers and add them to the string
			int index = 0;
			for(int[] r : reg) {
				str += "reg-" + index++ + ": " + r[0] + " " + r[1] + "\n";
			}
			
			return str;
		}
		
		private void addToLoadStores(int op) {
			switch(op) {
				case 0x24: // lbu
				case 0x25: // lhu
				case 0x30: // ll
				case 0x23: // lw
					loads++;
					break;
				case 0x28: // sb
				case 0x38: // sc
				case 0x29: // sh
				case 0x2b: // sw
					stores++;
					break;
			}
		}
		private void addReadWrite(Instruction in) {
			if(in.type == Type.r) {
				reg[in.rd][1]++;
				reg[in.rs][0]++;
				reg[in.rt][0]++;
				
				switch(in.funct) {
					case 0x08: // jr
						reg[in.rd][1]--;
						reg[in.rt][0]--;
						break;
					case 0x00: // sll
					case 0x02: // srl
					case 0x3:  // sra
						reg[in.rs][0]--;
						break;
				}
				
			} else if (in.type == Type.i) {
				//stores will mess me up
				//branches will mess me up
				//lui will mess me up
				reg[in.rt][1]++;
				reg[in.rs][0]++;
				
				switch(in.op) {
					case 0xf: // lui
						reg[in.rs][0]--;
						break;
					case 0x4: // beq
					case 0x5: // bne
						reg[in.rt][1]--;
						reg[in.rt][0]++;
						break;
					case 0x28: // sb
					case 0x38: // sc
					case 0x29: // sh
					case 0x2b: // sw
						reg[in.rs][0]--;
						reg[in.rs][1]++;
						
						reg[in.rt][0]++;
						reg[in.rt][1]--;
						break;
				}	
			// Handle jal
			} else if (in.op == 0x3) {
				reg[in.rt][1]--;
				reg[in.rs][0]--;
				
				reg[31][1]++;
			}
		}
	}
	
	public static void main(String[] args) {
		Scanner scan = null;
		PrintWriter pw;
		try {
			scan = new Scanner(new File("trace.txt"));
			pw = new PrintWriter(new FileWriter("statistics.txt"));
		} catch (IOException e) {
			System.out.println("File not found!");
		} 
		ArrayList<Instruction> list = new ArrayList<>();
		Instruction in;
		while(scan.hasNextLine()) {
			String[] str = scan.nextLine().split(" ");
			
			list.add(new Instruction(Integer.parseInt(str[0], 16),Integer.parseInt(str[1], 16)));
			int addr = Integer.parseInt(str[0], 16);
			int word = Integer.parseInt(str[1], 16);

			System.out.println(str[0]);
			System.out.println(addr);
			System.out.println();
			System.out.println(str[1]);
			System.out.println(Integer.toHexString(word));
			
			int foo = word & 0xFC000000; // op mask
			int bar = word & 0x3E000000; // rs mask
			int baz = word & 0x1F0000;   // rt mask
			int bongo = word & 0xF800;	 // rd mask
			
			System.out.println("Foo is");
			System.out.println(Integer.toBinaryString(foo));
	
			System.out.println("bar is");
			System.out.println(Integer.toBinaryString(bar));
			
			System.out.println("baz is");
			System.out.println(Integer.toBinaryString(baz));
			
			System.out.println("bongo is");
			System.out.println(Integer.toBinaryString(bongo));
			list.trimToSize();
			getStats(list);
		}
	}

	private static Stats getStats(ArrayList<Instruction> list) {
		Stats stats = new Stats();
		Instruction prev = null;
		for(Instruction in : list) {
			stats.insts++;
			stats.addToType(in.type);
			stats.addToLoadStores(in.op);
			stats.addReadWrite(in);
			
			//Branch stuff
			if(prev == null) {
				prev = in; 
				continue;
			}
			int diff = prev.addr + 4 - in.addr;
			if(diff > 0) {
				//old addr is bigger than new addr;
				//Took jump forward
			} else if (diff < 0) {
				//old addr is less than new addr;
				//Took jump back
			} else {
				//They are the same. No jump was taken
			}
			
			prev = in;
		}
		return stats;
	}

}
