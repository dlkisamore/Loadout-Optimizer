/**
This program will read in a text file containing a list of items and their stats.
The program filters out items which have no stat modifications as well as items that are inferior in every way to another item in the same slot category.
The program gets the user's input as to which groups of items to exclude from calculations.
The program then gets the user's input as to which stats to prioritize.
The program then determines the best loadout.

ITEMS.CSV FILE FORMAT
----------------
name;slot;group;exclusions,exclusions;stat;amount;stat;amount;...


CHANGE LOG
--------------------
- added ability to get user input about how many slots they have of each type of slot
- fixed bug where setting THREAD_COUNT to 1 caused permutation testing to terminate prematurely
- fixed bug where not all stats were taken into account during comparisons
- fixed bug where not all inferior equipment was eliminated

FUTURE DEVELOPMENT
--------------------
Account for stats of differing magnitude.
Account for multiple priority levels.
Account for having multiple item slots of the same name.
Add choice of stats to minimize.
Account for items that fit into multiple item slots.
Refine load distribution to include more than just the first slot number.
Release unused items for garbage collection.
GUI

//efficiency: 11,650,674 permutations per second (4.51x faster than V2) (20.77x faster than V1)
*/

import java.util.*;
import java.io.*;

public class Optimizer {
	static ArrayList<Item> items = new ArrayList<Item>();
	static ArrayList<String> allStatNames = new ArrayList<String>();
	static ArrayList<String> keyStats = new ArrayList<String>();
	static ArrayList<String> slots = new ArrayList<String>();
	static ArrayList<Integer> playerSlots = new ArrayList<Integer>();
	//arraylist of arraylists to store all items sorted by slots
	static ArrayList<ArrayList<Item>> sortedList = new ArrayList<ArrayList<Item>>(); //the first layer of this list is in the same order as ArrayList slots
	final static int THREAD_COUNT = 8;
	
	public static void main(String args[]) {
		//read in text file and process lines into items
		processItems(readFile());
		//get slots present on character
		getSlots();
		//get item groups to exclude
		getExclusionParams();
		//remove items that are inferior in every other way to another item
		optimize();
		//get target parameters
		getParams();
		//sort items based on slots they occupy
		sortItems();
		//split load evenly into threads
		int loadDistribution = sortedList.get(0).size() / THREAD_COUNT;
		int segmentStart = 0;
		int segmentStop = loadDistribution;
		ArrayList<Calculator> threads = new ArrayList<Calculator>();
		for(int i = 0; i < THREAD_COUNT; i++, segmentStart += loadDistribution, segmentStop += loadDistribution) {
			if(segmentStop >= sortedList.get(0).size()) {
				segmentStop = 0;
			}
			threads.add(new Calculator(slots, allStatNames, sortedList, keyStats, segmentStart, segmentStop, THREAD_COUNT));
		}
		//calculate best option in each thread
		for(Calculator c : threads) {
			c.start();
		}
		//wait for all threads to finish
		for(Calculator c : threads) {
			try {
				c.join();
			} catch(InterruptedException e) {
				//do nothing
			}
		}
		//check each thread result for overall best option and output results
		finalEvaluation(threads);
	}
	
	public static void getSlots() {
		Scanner scanner = new Scanner(System.in);
		//populate slots and playerSlots arraylist
		for(int i = 0; i < items.size(); i++) {
			String slot = items.get(i).getSlot();
			boolean found = false;
			for(String s : slots) {
				if(slot.equals(s)) {
					found = true;
				}
			}
			if(!found) {
				//ask player how many instances of this slot is present
				while(true) {
					System.out.print("How many " + slot + " slots does your character have? ");
					try {
						int choice = Integer.valueOf(scanner.nextLine().trim());
						if(choice == 0) { //if the slot is not present (0 available), remove all items of that slot type from the item arraylist and do NOT add the slot to the slots arraylist
							for(int j = 0; j < items.size(); j++) {
								if(items.get(j).getSlot().equals(slot)) {
									items.remove(j);
									j--;
								}
							}
						} else { //if the slot is present, add the slot count to the playerSlots arraylist and add the slot to the slots arraylist
							playerSlots.add(choice);
							slots.add(slot);
						}
						break;
					} catch(NumberFormatException e) {
						//loop back for invalid entries
					}
				}
			}
		}
	}
	
	public static void getExclusionParams() {
		//get all group names
		ArrayList<String> groupNames = new ArrayList<String>();
		for(Item i : items) {
			for(String s : i.getGroups()) {
				//only add the group name if it is not already in groupNames arrayList
				boolean found = false;
				for(String st : groupNames) {
					if(st.equals(s)) {
						found = true;
					}
				}
				if(!found) {
					groupNames.add(s);
				}
			}
		}
		//display menu of all group names
		System.out.println("Which groups would you like to exclude from your pool?");
		System.out.println("------------------------------------------------------");
		int tempNum = 1;
		for(String s : groupNames) {
			System.out.println(tempNum + ": " + s);
			tempNum++;
		}
		System.out.println("------------------------------------------------------");
		System.out.print("Exclusions (,): ");
		Scanner scanner = new Scanner(System.in);
		String selections = scanner.nextLine();
		selections += ",";
		System.out.println();
		//parse input and eliminate items of excluded groups
		while(true) {
			int selectionNum = Integer.valueOf(selections.substring(0, selections.indexOf(",")));
			selectionNum--;
			String excludedGroup = groupNames.get(selectionNum);
			for(int i = 0; i < items.size(); i++) {
				for(int j = 0; j < items.get(i).getGroups().size(); j++) {
					//compare excludedGroup to each group that each item belongs to
					if(items.get(i).getGroup(j).equals(excludedGroup)) {
						//remove the item from items array list if group is found
						items.remove(i);
						i--;
						break;
					}
				}
			}
			if(selections.indexOf(",") == selections.length() - 1) {
				break;
			} else {
				selections = selections.substring(selections.indexOf(",") + 1);
			}
		}
	}
	
	public static void finalEvaluation(ArrayList<Calculator> results) {
		//sort threads by keystat totals high to low
		while(true) {
			boolean switched = false;
			for(int i = 0; i < results.size() - 1; i++) {
				if(results.get(i).getKeyStatTotal() < results.get(i + 1).getKeyStatTotal()) {
					Calculator temp = results.get(i);
					results.set(i, results.get(i + 1));
					results.set(i + 1, temp);
					switched = true;
				} else if(results.get(i).getKeyStatTotal() == results.get(i + 1).getKeyStatTotal()) {
					//if keyStatTotal is the same for both results, sort by allStatTotal
					if(results.get(i).getAllStatTotal() < results.get(i + 1).getAllStatTotal()) {
						Calculator temp = results.get(i);
						results.set(i, results.get(i + 1));
						results.set(i + 1, temp);
						switched = true;
					}
				}
			}
			if(!switched) {
				break;
			}
		}
		//output item at index 0 as final result
		results.get(0).printBest();
	}
	
	public static void sortItems() {
		for(String slot : slots) {
			//create an arrayList of items that have the same slot name as slot
			ArrayList<Item> matchingItems = new ArrayList<Item>();
			for(Item item : items) {
				if(slot.equals(item.getSlot())) {
					matchingItems.add(item);
				}
			}
			//add the new arraylist to sortedList
			sortedList.add(matchingItems);
		}
		for(ArrayList<Item> ar : sortedList) {
			System.out.print(ar.size() + "  ");
		}
		System.out.println();
	}
	
	public static void getParams() {
		//get list of all parameters (from stat names)
		for(Item i : items) {
			ArrayList<String> iStatNames = i.getStatNames();
			//check each string to see if it already exists in paramNames
			for(String s : iStatNames) {
				boolean found = false;
				for(String st : allStatNames) {
					if(s.equals(st)) {
						found = true;
					}
				}
				if(!found) {
					allStatNames.add(s);
				}
			}
		}
		//get priority stats
		//show menu
		System.out.println("Stats");
		System.out.println("-------------------------------");
		for(int i = 0; i < allStatNames.size(); i++) {
			int menuNumber = i + 1;
			System.out.println(menuNumber + ": " + allStatNames.get(i));
		}
		System.out.println("-------------------------------");
		//get user input
		System.out.print("Priorities (,): ");
		Scanner scanner = new Scanner(System.in);
		String selections = scanner.nextLine();
		selections += ",";
		System.out.println();
		//parse input and add items to keyStats
		while(true) {
			int selectionNum = Integer.valueOf(selections.substring(0, selections.indexOf(",")));
			keyStats.add(allStatNames.get(--selectionNum));
			if(selections.indexOf(",") == selections.length() - 1) {
				break;
			} else {
				selections = selections.substring(selections.indexOf(",") + 1);
			}
		}
	}
	
	//removes items from items arrayList if there is another item that is quantifiably better in every way that can be equipped to the same slot
	public static void optimize() {
		for(int i = 0; i < items.size() - 1; i++) { //for each item in items array list...
			//compare item to all other items...
			for(int j = i + 1; j < items.size(); j++) {
				//if the item can be equipped to the same slot...
				if(items.get(i).getSlot().equals(items.get(j).getSlot())) {
					ArrayList<Stat> firstItemStats = items.get(i).getStats();
					ArrayList<Stat> secondItemStats = items.get(j).getStats();
					//ArrayList<Stat> secondItemStatsOrganized = new ArrayList<Stat>(); //will no longer be needed with new code...delete
					/**NEW CODE*/
					//check to see which item has quantifiably better stats...
					boolean itemOneBetter = false;
					boolean itemTwoBetter = false;
					for(Stat s1 : firstItemStats) {
						boolean found = false;
						//s1 is present in item2...
						for(Stat s2 : secondItemStats) {
							if(s1.getName().equals(s2.getName())) {
								found = true;
								if(s1.getAmount() > s2.getAmount()) { //if s1 is better
									itemOneBetter = true;
								} else if(s2.getAmount() > s1.getAmount()) { //if s2 is better
									itemTwoBetter = true;
								}
							}
						}
						//s1 is not present in item2...
						if(!found) {
							if(s1.getAmount() < 0) {
								itemTwoBetter = true;
							} else {
								itemOneBetter = true;
							}
						}
					}
					//check negatives for stats in item two that are not present in item one
					for(Stat s2 : secondItemStats) {
						boolean found = false;
						//s2 is present in item2...
						for(Stat s1 : firstItemStats) {
							if(s2.getName().equals(s1.getName())) {
								found = true;
							}
						}
						//s1 is not present in item2...
						if(!found) {
							if(s2.getAmount() < 0) {
								itemOneBetter = true;
							} else {
								itemTwoBetter = true;
							}
						}
					}
					//item elimination conditions
					if(!itemOneBetter && itemTwoBetter) {
						//FALSE TRUE = second item has better stats : remove first one
						items.remove(i);
						i--;
						break;
					} else if((!itemOneBetter && !itemTwoBetter) ||(itemOneBetter && !itemTwoBetter)) {
						//FALSE FALSE = both have equivalent and equal stats : remove second one
						//TRUE FALSE = first item has better stats : remove second one
						items.remove(j);
						j--;
					} //TRUE TRUE = both items are unique : do not remove either
					/**END NEW CODE*/
				}
			}
		}
	}
	
	//populates ArrayList<Item> items with ArrayList<String> rawLines
	public static void processItems(ArrayList<String> rawLines) {
		for(String s : rawLines) {
			//create item
			String tempLine = s.trim();
			String name = tempLine.substring(0,tempLine.indexOf(";"));
			tempLine = tempLine.substring(tempLine.indexOf(";") + 1);
			String slot = tempLine.substring(0,tempLine.indexOf(";"));
			tempLine = tempLine.substring(tempLine.indexOf(";") + 1);
			if(tempLine.length() == 0) { //prevents items that have no groups, exclusions, or stat modifiers from being considered
				continue;
			}
			ArrayList<String> groups = new ArrayList<String>();
			if(tempLine.indexOf(";") != 0) {
				String temp = tempLine.substring(0,tempLine.indexOf(";"));
				do {
					if(temp.contains(",")) {
						groups.add(temp.substring(0, temp.indexOf(",")));
						temp = temp.substring(temp.indexOf(",") + 1);
					} else {
						groups.add(temp);
					}
				} while(temp.contains(","));
			}
			tempLine = tempLine.substring(tempLine.indexOf(";") + 1);
			if(tempLine.length() == 0) { //prevents items that have no exclusions or stat modifiers from being considered
				continue;
			}
			ArrayList<String> exclusions = new ArrayList<String>();
			if(tempLine.indexOf(";") != 0) {
				String temp = tempLine.substring(0,tempLine.indexOf(";"));
				do {
					if(temp.contains(",")) {
						exclusions.add(temp.substring(0, temp.indexOf(",")));
						temp = temp.substring(temp.indexOf(",") + 1);
					} else {
						exclusions.add(temp);
					}
				} while(temp.contains(","));
			}
			tempLine = tempLine.substring(tempLine.indexOf(";") + 1);
			if(tempLine.length() == 0) { //prevents items that have no stat modifiers from being considered
				continue;
			}
			items.add(new Item(name,slot,groups,exclusions));
			//identify current item in items array list
			int itemPos = items.size() - 1;
			//add stats to items
			while(true) {
				//get stat
				String stat = tempLine.substring(0,tempLine.indexOf(";"));
				tempLine = tempLine.substring(tempLine.indexOf(";") + 1);
				int amount = Integer.valueOf(tempLine.substring(0, tempLine.indexOf(";")));
				//add stat to item
				items.get(itemPos).addStat(stat,amount);
				//exit while loop if no other stats remain
				if(tempLine.indexOf(";") == tempLine.length() - 1) {
					break;
				} else {
					tempLine = tempLine.substring(tempLine.indexOf(";") + 1);
				}
			}
		}
	}
	
	//sends ArrayList<String> rawLines to processItems()
	public static ArrayList<String> readFile() {
		ArrayList<String> rawLines = new ArrayList<String>();
		try(BufferedReader br = new BufferedReader(new FileReader("items.csv"))) {
			String line = null;
			while((line = br.readLine()) != null) {
				//trim off excess ;
				while(line.lastIndexOf(";") == line.length() - 1) {
					line = line.substring(0, line.length() - 1);
				}
				//terminate line with ;
				rawLines.add(line + ";");
			}
		} catch(Exception e) {
			e.printStackTrace();
		}
		return rawLines;
	}
}

class Calculator extends Thread {
	private int[] bestPermutation;
	private int[] bestStats;
	private int keyStatTotal; //used for comparing results of all threads
	private int allStatTotal; //used for comparing results of all threads
	private int startingPos;
	private int endingPos;
	
	private ArrayList<String> slots;
	private ArrayList<String> allStatNames;
	private ArrayList<ArrayList<Item>> sortedList = new ArrayList<ArrayList<Item>>();;
	private ArrayList<String> keyStats;
	
	private final int THREAD_COUNT;
	
	public Calculator(ArrayList<String> slots, ArrayList<String> allStatNames, ArrayList<ArrayList<Item>> sortedList, ArrayList<String> keyStats, int startingPos, int endingPos, int threadCount) {
		this.slots = (ArrayList<String>) slots.clone();
		this.allStatNames = (ArrayList<String>) allStatNames.clone();
		//create a deep copy of sortedList
		//create same number of sublists
		for(int i = 0; i < sortedList.size(); i++) {
			this.sortedList.add(new ArrayList<Item>());
			//copy items from sublists
			for(int j = 0; j < sortedList.get(i).size(); j++) {
				this.sortedList.get(i).add(new Item(sortedList.get(i).get(j)));
			}
		}
		this.keyStats = (ArrayList<String>) keyStats.clone();
		this.startingPos = startingPos;
		this.endingPos = endingPos;
		this.THREAD_COUNT = threadCount;
	}
	
	public int getKeyStatTotal() {
		return this.keyStatTotal;
	}
	
	public int getAllStatTotal() {
		return this.allStatTotal;
	}
	
	public void run() {
		int slotCount = this.slots.size(); //number of digits in each permutations
		this.bestPermutation = new int[slotCount];
		this.bestStats = new int[this.allStatNames.size()];
		//set up a best permutation
		for(int i = 0; i < this.bestPermutation.length; i++) {
			this.bestPermutation[i] = 0;
		}
		//get starting permutation
		int[] currentPermutation = new int[slotCount];
		int[] currentStats = new int[this.allStatNames.size()];
		currentPermutation[0] = this.startingPos; //set at time of thread creation (USED FOR MULTITHREADING)
		for(int i = 1; i < currentPermutation.length; i++) {
			currentPermutation[i] = 0;
		}
		int currentStatsLength = currentStats.length;
		int currentPermutationLength = currentPermutation.length;
		int allStatNamesSize = this.allStatNames.size();
		//check all permutations
		while(true) {
			//reset values for currentStats
			for(int i = 0; i < currentStatsLength; i++) {
				currentStats[i] = 0;
			}
			//check code for conflicting groups
			boolean valid = true;
			for(int i = 0; i < currentPermutationLength - 1; i++) {
				for(int j = i + 1; j < currentPermutationLength; j++) {
					for(String s : this.sortedList.get(i).get(currentPermutation[i]).getExclusions()) {
						if(this.sortedList.get(j).get(currentPermutation[j]).getGroups().contains(s)) {
							valid = false;
							break;
						}
					}
					if(!valid) {
						break;
					}
				}
				if(!valid) {
					break;
				}
			}
			//ONLY TEST THE PERMUTATION IF IT DOES NOT CONTAIN ITEMS IN CONFLICTING GROUPS
			if(valid) {
				//get stats for each item in currentPermutation and find the total
				for(int i = 0; i < currentPermutationLength; i++) { //for each slot in the permutation...
					//get the item from the sorted arraylist of arraylists based on permutation
					Item currentItem = this.sortedList.get(i).get(currentPermutation[i]);
					//for each stat in the item...
					int currentItemStatsSize = currentItem.stats.size();
					for(int j = 0; j < currentItemStatsSize; j++) {
						//search through allStatNames to identify the appropriate location in currentStats to add stat to using Stat.name
						Stat currentItemStat = currentItem.stats.get(j);
						for(int k = 0; k < allStatNamesSize; k++) {
							if(currentItemStat.getName().equals(this.allStatNames.get(k))) { //when a match is found...
								currentStats[k] += currentItemStat.getAmount(); //add the stat to currentStats
								break;
							}
						}
					}
				}
				//compare keyStat values for currentStats with bestStats; if higher, replace bestPermutation and bestStats
				int advantage = 0; //tracks the positive and negative effect of the new loadout over the old loadout (advantage = new - old)
				for(String s : this.keyStats) { //this is looping once per keystat 32 * 4 = 128
					for(int i = 0; i < allStatNamesSize; i++) { //i indicates the position of the stat in all arrays
						if(s.equals(this.allStatNames.get(i))) {
							advantage += currentStats[i] - this.bestStats[i];
						}
					}
				}
				if(advantage >= 0) {
					//check all stats to see if there is an advantage to changing to the current loadout
					int bestStatsTotal = 1; //this will not change if advantage > 0; if advantage is 0, a further check is required to determine if there is ANY advantage to switching loadouts
					if(advantage == 0) { //if there is no keystat advantage...
						/**NEW CODE STARTS HERE*/
						//check for the smallest stat range
						//sort best and current stats low to high
						int[] sortedBestStats = new int[bestStats.length];
						int[] sortedCurrentStats = new int[currentStats.length]; //this will always be the same length as sortedBestStats
						for(int i = 0; i < bestStats.length; i++) { //copy values to be sorted
							sortedBestStats[i] = bestStats[i];
							sortedCurrentStats[i] = currentStats[i];
						}
						boolean switched = false;
						do { //sort sortedBestStats
							switched = false;
							for(int i = 0; i < sortedBestStats.length - 1; i++) {
								if(sortedBestStats[i] > sortedBestStats[i + 1]) {
									switched = true;
									int temp = sortedBestStats[i];
									sortedBestStats[i] = sortedBestStats[i + 1];
									sortedBestStats[i + 1] = temp;
								}
							}
						} while(switched);
						do { //sort sortedCurrentStats
							switched = false;
							for(int i = 0; i < sortedCurrentStats.length - 1; i++) {
								if(sortedCurrentStats[i] > sortedCurrentStats[i + 1]) {
									switched = true;
									int temp = sortedCurrentStats[i];
									sortedCurrentStats[i] = sortedCurrentStats[i + 1];
									sortedCurrentStats[i + 1] = temp;
								}
							}
						} while(switched);
						//calculate ranges
						int range1 = sortedBestStats[sortedBestStats.length - 1] - sortedBestStats[0];
						int range2 = sortedCurrentStats[sortedCurrentStats.length - 1] - sortedCurrentStats[0];
						if(range1 < range2) {
							bestStatsTotal = 1; //NEW BEST FOUND
						} else if(range1 == range2) {
							//check for non-keystat advantage
							bestStatsTotal = 0;
							for(int i : this.bestStats) {
								bestStatsTotal += i;
							}
							int currentStatsTotal = 0;
							for(int i : currentStats) {
								currentStatsTotal += i;
							}
							if(bestStatsTotal < currentStatsTotal) {
								bestStatsTotal = 1;
							} else {
								bestStatsTotal = 0;
							}
						}
						/**END NEW CODE*/
					}
					//this will occur if there is a keystat advantage OR if there is a break-even keystat advantage and a non-keystat advantage
					if(bestStatsTotal > 0) { //NEW BEST FOUND
						//make current loadout and stats into best loadout and stats
						for(int i = 0; i < currentPermutationLength; i++) { //loadout
							this.bestPermutation[i] = currentPermutation[i];
						}
						for(int i = 0; i < bestStats.length; i++) { //stats
							this.bestStats[i] = currentStats[i];
						}
					}
				}
			}
			//increase currentPermutation; break if out of bounds
			for(int i = currentPermutationLength - 1; i >= 0; i--) { //works from the last item in permutation and progresses backward
				//increase the last digit in the permutation
				currentPermutation[i]++;
				//if out of range of possible permutative values, reset to 0; if in range, exit loop
				if(currentPermutation[i] >= this.sortedList.get(i).size()) {
					currentPermutation[i] = 0;
				} else {
					break; //stops incrementation if current incrementation is within range
				}
			}
			//if first permutative value equals endingPos and all other permutative values are 0, all permutations have been evaluated, exit while loop
			if(currentPermutation[0] == endingPos) {
				boolean allZeros = true;
				for(int i = 1; i < currentPermutationLength; i++) {
				//for(int i : currentPermutation) {
					if(currentPermutation[i] != 0) {
						allZeros = false;
					}
				}
				if(allZeros) {
					break;
				}
			}
		}
		//calculate keyStatTotal
		for(String s : this.keyStats) {
			//find the string's position in allStatNames
			for(int i = 0; i < this.allStatNames.size(); i++) {
				if(s.equals(this.allStatNames.get(i))) {
					//add the number at index i in bestStats to bestStatTotal
					this.keyStatTotal += this.bestStats[i];
					break; //move on to next String s in keyStats
				}
			}
		}
		//calculate allStatTotal
		for(int i : this.bestStats) {
			this.allStatTotal += i;
		}
	}
	
	public void printBest() {
		//output bestPermutation and bestStats both on-screen and to file (results.txt)
		try(PrintWriter output = new PrintWriter("results.txt")) {
			System.out.println();
			System.out.println("RESULTS");
			output.println("RESULTS");
			System.out.println("----------------------------");
			output.println("----------------------------");
			for(int i = 0; i < bestPermutation.length; i++) {
				//print the name of the item
				System.out.println(sortedList.get(i).get(bestPermutation[i]).getName());
				output.println(sortedList.get(i).get(bestPermutation[i]).getName());
			}
			System.out.println();
			output.println();
			for(int i = 0; i < bestStats.length; i++) {
				System.out.println(allStatNames.get(i) + ": " + bestStats[i]);
				output.println(allStatNames.get(i) + ": " + bestStats[i]);
			}
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
}

class Item {
	private String name;
	private String slot;
	public ArrayList<Stat> stats = new ArrayList<Stat>();
	public ArrayList<String> groups = new ArrayList<String>();
	public ArrayList<String> exclusions = new ArrayList<String>();
	
	
	public Item(String name, String slot, ArrayList<String> groups, ArrayList<String> exclusions) {
		this.name = name;
		this.slot = slot;
		for(String s : groups) {
			this.groups.add(s);
		}
		for(String s : exclusions) {
			this.exclusions.add(s);
		}
	}
	
	public Item(Item original) {
		this.name = original.getName();
		this.slot = original.getSlot();
		for(int i = 0; i < original.getStats().size(); i++) {
			this.stats.add(new Stat(original.getStats().get(i)));
		}
		for(int i = 0; i < original.getGroups().size(); i++) {
			this.groups.add(original.getGroup(i));
		}
		for(int i = 0; i < original.getExclusions().size(); i++) {
			this.exclusions.add(original.getExclusion(i));
		}
	}
	
	public String getExclusion(int index) {
		return this.exclusions.get(index);
	}
	
	public ArrayList<String> getExclusions() {
		return this.exclusions;
	}
	
	public String getGroup(int index) {
		return this.groups.get(index);
	}
	
	public ArrayList<String> getGroups() {
		return this.groups;
	}
	
	public void addStat(String stat, int amount) {
		stats.add(new Stat(stat, amount));
	}
	
	public String getName() {
		return this.name;
	}
	
	public String getSlot() {
		return this.slot;
	}
	
	public ArrayList<String> getStatNames() {
		ArrayList<String> statNames = new ArrayList<String>();
		for(Stat s : stats) {
			statNames.add(s.getName());
		}
		return statNames;
	}
	
	public ArrayList<Stat> getStats() {
		return this.stats;
	}
}

class Stat {
	private String name;
	private int amount;
	
	public Stat(String name, int amount) {
		this.name = name;
		this.amount = amount;
	}
	
	public Stat(Stat original) {
		this.name = original.getName();
		this.amount = original.getAmount();
	}
	
	public String getName() {
		return this.name;
	}
	
	public int getAmount() {
		return this.amount;
	}
}