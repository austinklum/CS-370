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
		long addr, word;
		int op, rs, rt, rd, shamt, funct, imm, jAddr;
		Type type;
		private Instruction(long addr, long word) {
			this.addr = addr;
			this.word = word;
			
			op = bitsAt(26,31);
			type = getTypeFromOp();

			rs = bitsAt(21,25);
			rt = bitsAt(16,20);
			rd = bitsAt(11,15);
			shamt = bitsAt(6,10);
			funct = bitsAt(0,5);
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
			   int bitMask = 0;
			   for (int i=start; i<=end; i++) {
				   bitMask |= 1 << i;
			   }
			   
			   return ((int)(word & bitMask)) >>> start;
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
					break;
				case i:
					iType++;
					break;
				case j:
					jType++;
					break;
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
			int read = 0, write = 1;
			switch(in.type) {
				case r:
					reg[in.rd][write]++;
					reg[in.rs][read]++;
					reg[in.rt][read]++;
					
					switch(in.funct) {
						case 0x08: // jr
							reg[in.rd][write]--;
							reg[in.rt][read]--;
							break;
						case 0x00: // sll
						case 0x02: // srl
						case 0x3:  // sra
							reg[in.rs][read]--;
							break;
					}
					break;
				case i:
					//stores will mess me up
					//branches will mess me up
					//lui will mess me up
					reg[in.rt][write]++;
					reg[in.rs][read]++;
					
					switch(in.op) {
						case 0xf: // lui
							reg[in.rs][read]--;
							break;
						case 0x4: // beq
						case 0x5: // bne
							reg[in.rt][write]--;
							reg[in.rt][read]++;
							break;
						case 0x28: // sb
						case 0x38: // sc
						case 0x29: // sh
						case 0x2b: // sw
							reg[in.rt][read]++;
							reg[in.rt][write]--;
							break;
					}	
					break;
				// Handle jal
				default:
					if (in.op == 0x3) {
						reg[in.rt][write]--;
						reg[in.rs][read]--;
						
						reg[31][write]++;
					}
				break;
			}
		}

		public void addBranchCount(long prevAddr, long inAddr, int prevOp) {
			long diff = prevAddr + 4 - inAddr;
			if(diff < 0) {
				//old addr is less than new addr;
				//Took jump forward
				fwdTaken++;
			} else if (diff > 0) {
				//old addr is bigger than new addr;
				//Took jump back
				bkwTaken++;
				 
			// beq OR bne
			} else if (prevOp == 0x4 || prevOp == 0x5 ){
				//They are the same and had a branch. No jump was taken
				notTaken++;
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
		while(scan.hasNextLine()) {
			String[] str = scan.nextLine().split(" ");
			list.add(new Instruction(Long.parseLong(str[0], 16), Long.parseLong(str[1], 16)));
		}
		System.out.println(getStats(list));
	}

	private static Stats getStats(ArrayList<Instruction> list) {
		Stats stats = new Stats();
		Instruction prev = null;
		for(Instruction in : list) {
			stats.insts++;
			stats.addToType(in.type);
			stats.addToLoadStores(in.op);
			stats.addReadWrite(in);
			if(prev == null) {
				prev = in; 
				continue;
			}
			stats.addBranchCount(prev.addr,in.addr,prev.op);
			
			prev = in;
		}
		//stats.addBranchCount(prev.addr, prev.addr, prev.op);
		return stats;
	}

}
