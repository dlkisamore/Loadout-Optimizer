import java.util.*;
import java.io.*;

public class Randomizer {
	public static void main(String args[]) {
		//read in file
		ArrayList<String> lines = new ArrayList<String>();
		try(BufferedReader br = new BufferedReader(new FileReader("items.csv"))) {
			String line = null;
			while((line = br.readLine()) != null) {
				lines.add(line);
			}
		} catch(Exception e) {
			e.printStackTrace();
		}
		//random number that is between 0 and size of arraylist
		ArrayList<String> scrambledLines = new ArrayList<String>();
		while(lines.size() > 0) {
			Random rand = new Random();
			int val = rand.nextInt(lines.size());
			
			scrambledLines.add(lines.get(val));
			lines.remove(val);
		}
		//output scrambledLines to csv file
		try(PrintWriter pw = new PrintWriter("scrambled.csv")) {
			for(String s : scrambledLines) {
				pw.println(s);
			}
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
}