package pcy;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class PCY {
	public static void main(String [] args) throws Exception {
		long begin = System.currentTimeMillis();
		
		//int[] supports = {1, 5, 10}; //array of support thresholds we are interested in
		//int[] percentages = {1, 5, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100}; //percentage sizes of the data (chunk sizes)
		
		int[] supports = {5};
		int[] percentages = {100};
		
	    BufferedReader input = new BufferedReader(new FileReader("retail.txt"));
	    
		for(double sup : supports) {
			for(double per : percentages) {
				double num_baskets = 88162 * (per/100); //Notepad++ shows the file has 88162 lines/baskets. This is how many baskets we have to read for this chunk.
				int support_threshold = (int) ((88162*(per/100))*(sup/100)); //number of occurrences of a value to be considered frequent
				System.out.println("Num baskets: "+num_baskets);
				System.out.println("Support threshold: "+support_threshold);
				
				//Create a hashmap to store <value, frequency> pairs.
				HashMap<Integer,Integer> hm = new HashMap<Integer,Integer>(); //have to use Integer instead of int because hashmap does not take primitives, just objects. Thankfully Intger wraps an int in an object.

				//Read file line-by-line and store frequency of each singleton
				findAllSingletons(hm, input, num_baskets);
				

				
				//int mod = (hm.size()*(hm.size()-1))/2 / support_threshold ; //mod will be 0 if number of frequent singletons is small and support threshold is large
				//if(mod == 0) {mod++;} //insurance so that we don't divide by 0
				int mod = 61;
				int[] hashtable = new int[mod];
				
				//Find all n*(n-1)/2 candidate pairs and hash pairs to buckets
				hashCandidatePairs(hm, mod, hashtable);
				
				//System.out.println(Arrays.toString(hashtable));
				//Map hashtable to bit vector
				hashtable = mapToBitVector(hashtable, support_threshold); 
				//System.out.println("bitvector: "+Arrays.toString(hashtable));
				//System.out.println("mod is "+mod+", support is "+support_threshold);

				//System.out.println("hashmap (count "+ hm.size()+"): "+hm);
				
				//prune non-frequent singletons 
				pruneSingletons(hm, support_threshold);
				System.out.println("frequent singletons: "+hm);
				
				//find all pairs that hash to frequent buckets
				int[][] pairs = new int[(hm.size()*(hm.size()-1)/2)][3]; //new table to store pairs, can't use a hashmap because there may be pairs with the same keys but we need to track them all. Inner tables store 3 values which represent {element1, element2, count}
				pairs = findCandidatePairs(hm, mod, hashtable);
				//System.out.println(Arrays.deepToString(pairs));
				pairs = countCandidatePairs(hm, support_threshold, pairs);
				System.out.println("Candidate pairs and their frequencies: "+Arrays.deepToString(pairs));
				
				//System.out.println(Arrays.deepToString(pairs));
				//System.out.println(pairs.length);
				
				//find frequent pairs by counting frequency of all candidate pairs
				//This approach reads each line and counts the frequency of each candidate pair encountered, and removes frequent pairs from the collection and stores them in a new collection.
				//This way, we can reduce the amount of data we have to check as we go through the file. This approach is quicker than reading the whole file for each pair and calling break;.
				//HashMap<Integer,Integer> frequentpairs = new HashMap<Integer,Integer>(); //new hashmap to store frequent pairs. Instead of storing <key, value> we are storing <key, key>
				
				ArrayList<Integer>[][] frequentpairs = findFrequentPairs(pairs, support_threshold);
				System.out.println("frequent pairs (count "+frequentpairs.length+"): "+Arrays.deepToString(frequentpairs));
				
				input.close(); //close bufferedreader
				
			}
		}
		
		long end = System.currentTimeMillis();
		long time = end-begin;
		System.out.println("Elapsed time: "+time+" ms");
	}
	public static void findAllSingletons(HashMap<Integer,Integer> hash, BufferedReader br, double num_baskets) throws NumberFormatException, IOException {
		String line;
		int num_baskets_read = 0;
		while((line=br.readLine()) != null && num_baskets_read < num_baskets) { //read the whole chunk one line at a time
			String[] linesplit = line.split(" ");
			for(int i = 0; i<linesplit.length; i++) {
				if(hash.containsKey(Integer.parseInt(linesplit[i]))) {
					hash.put(Integer.parseInt(linesplit[i]), hash.get(Integer.parseInt(linesplit[i]))+1);
				}
				else {
					hash.put(Integer.parseInt(linesplit[i]), 1);
				}
			}
			num_baskets_read++;
		}
	}
	
	public static void pruneSingletons(HashMap<Integer,Integer> hash, int support_threshold) {
		for(Iterator<Map.Entry<Integer, Integer>> itr = hash.entrySet().iterator(); itr.hasNext(); ) { //Have to define an iterator because we can't modify the hashmap while iterating through it
			Map.Entry<Integer, Integer> entry = itr.next();
			if(entry.getValue() < support_threshold) {
	        	itr.remove();
	        }
		}
	}
	
	public static void hashCandidatePairs(HashMap<Integer,Integer> hash, int mod, int[]hashtable) {
		int super_iterations=0;
		int a = 0;
		for(Iterator<Map.Entry<Integer, Integer>> itr = hash.entrySet().iterator(); itr.hasNext();) {
			Map.Entry<Integer, Integer> obj = itr.next(); //Calling .next() after the iterator has been declared sets the pointer to index 0
			if(!itr.hasNext()) { //Reached last element in the set so there are no more elements after this, so we're done.
				break;
			}
			for(Iterator<Map.Entry<Integer, Integer>> subitr = hash.entrySet().iterator(); subitr.hasNext();) {
				Map.Entry<Integer, Integer> subobj = subitr.next(); //set pointer to index 0
				
				if(a == 0 && subitr.hasNext()) { //If it's the first pair for a new n, then increment pointer by 1
					for(int i = 0; i<super_iterations; i++) { //Also increment pointer by 1 for each n
						if(subitr.hasNext()) {
							subobj = subitr.next();
						}
						
					}
					if(subitr.hasNext()) {
						subobj = subitr.next();
					}				
					a++;
				}
				
				hashtable[hashfunc(mod, obj.getKey(), subobj.getKey())] += 1; //hash each pair to a bucket and add 1 to that bucket's count
			}
			a=0;
			super_iterations++;
		}
	}
	
	public static int[][] findCandidatePairs(HashMap<Integer,Integer> hashmap, int mod, int[]hashtable) {
		int[][] pairs = new int[hashmap.size()*(hashmap.size()-1)/2][3];
		int a = 0;
		int super_iterations = 0; //reset super_iterations
		int sub_iterations = 0;
		for(Iterator<Map.Entry<Integer, Integer>> itr = hashmap.entrySet().iterator(); itr.hasNext();) {
			Map.Entry<Integer, Integer> obj = itr.next(); //Calling .next() after the iterator has been declared sets the pointer to index 0
			if(!itr.hasNext()) { //Reached last element in the set so there are no more elements after this, so we're done.
				break;
			}
			for(Iterator<Map.Entry<Integer, Integer>> subitr = hashmap.entrySet().iterator(); subitr.hasNext();) {
				Map.Entry<Integer, Integer> subobj = subitr.next(); //set pointer to index 0
				
				if(a == 0 && subitr.hasNext()) { //If it's the first pair for a new n, then increment pointer by 1
					for(int j = 0; j<super_iterations; j++) { //Also increment pointer by 1 for each new n
						if(subitr.hasNext()) {
							subobj = subitr.next();
						}
					}
					if(subitr.hasNext()) {
						subobj = subitr.next();
					}
					a++;
				}
				
				if(hashtable[hashfunc(mod, obj.getKey(), subobj.getKey())] == 1) {
					//System.out.println(""+obj.getKey()+" "+subobj.getKey());
					pairs[sub_iterations][0] = obj.getKey();
					pairs[sub_iterations][1] = subobj.getKey();
				}
				sub_iterations++;
			}
			a=0;
			super_iterations++;
		}
		return pairs;
	}
	
	public static int[][] countCandidatePairs(HashMap<Integer,Integer> hashmap, int support_threshold, int[][]pairs) throws NumberFormatException, IOException {
		String line;
		BufferedReader input2 = new BufferedReader(new FileReader("retail.txt"));
		while((line=input2.readLine()) != null) {
			
			String[] linesplit = line.split(" ");
			int[] array = new int[linesplit.length];
			for(int i = 0; i <linesplit.length; i++) {
				array[i] = Integer.parseInt(linesplit[i]);
			}

			for(int j = 0; j<(hashmap.size()*(hashmap.size()-1)/2); j++) {
				boolean containsElement1 = false; //reset booleans 
				boolean containsElement2 = false;
				for(int i = 0; i<array.length; i++) {
					//check if the line contains both elements. Don't count arrays that are all zeroes, stop counting the group when the support threshold is reached
					if((pairs[j][0] != 0 && pairs[j][1] != 0 ) && pairs[j][2] != support_threshold && pairs[j][0] == array[i]) {
						containsElement1 = true;
					}
					if((pairs[j][0] != 0 && pairs[j][1] != 0 ) && pairs[j][2] != support_threshold && pairs[j][1] == array[i]) {
						containsElement2 = true;
					}
					if(containsElement1 == true && containsElement2 == true) {
						pairs[j][2]++;
						break; //break to make sure we don't keep counting elements while both booleans are true
						
					}
					
					if(array[i] > pairs[j][0] && array[i] > pairs[j][1]) { //since each line is already in ascending order we can break when we have passed both elements in the pair. Saves time.
						break;
					}
				}
			}
		}
		input2.close();
		return pairs;
		
	}

	public static ArrayList<Integer>[][] findFrequentPairs(int[][] pairs, int support_threshold) throws NumberFormatException, IOException {
		int top = 0; //defining integers to act as index for the frequentpairs ArrayList
		int bottom = 0;
		
		for(int i = 0; i<pairs.length; i++) {
			if(pairs[i][2] >= support_threshold) {
				top++;
			}
		}
		
		ArrayList<Integer>[][] frequentpairs = new ArrayList[top][1];
        
		for(int i = 0; i<pairs.length; i++) {
			if(pairs[i][0] != 0 && pairs[i][2] >= support_threshold) {
				frequentpairs[bottom][0] = new ArrayList<Integer>();
				frequentpairs[bottom][0].add(pairs[i][0]);
				frequentpairs[bottom][0].add(pairs[i][1]);
				bottom++;
			}
		}
		return frequentpairs;
	}
	
	
	public static int hashfunc(int mod, int i, int j) { //the hash function we use for PCY algorithm.
		return (i * j) % mod;
	}
	
	public static int[] mapToBitVector(int[] arr, int threshold) {
		for(int i = 0; i<arr.length; i++) {
			//System.out.println(arr[i]);
			if(arr[i] >= threshold) {
				arr[i] = 1;
			}
			else {
				arr[i] = 0;
			}
		}
		return arr;
	}
	
	
}
