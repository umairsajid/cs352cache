import java.util.ArrayList;
import java.lang.*;

class cache_sim {

    private int cache_capacity;
    private int cache_blocksize;
    private int cache_associativity;

    final static int CACHE_READ = 0;
    final static int CACHE_WRITE = 1;


    //A class used to contain cache data
    private static class cache_entry {
	private boolean valid;
	private boolean dirty;
	private String[] data;
	private String tag;
	private int address;
	private int age;
	
	/*
	 * Constructor, uses deep copy on incoming block data
	 * @param numblocks, the number of blocks this entry is expecting
	 * @param tag,       the tag for this entry
	 * @param location,  the address in memory that this entry points to as an int
	 * @param blocks,    the datablock for this entry
	 * @param isValid,   is this a valid entry
	 */
	public cache_entry(int numblocks, String tag, int location, String[] blocks, boolean isValid){
	    this.tag = tag;
	    this.address = location;
	    this.age = 0;
	    valid = isValid;
	    dirty = false;
	    data = new String[numblocks];
	    String zeroes = "";
		for(int i = 0; i < numblocks; i++){
		if( blocks[i] != null && blocks[i].length() < 8){
			for(int x = 0; x < (8-blocks[i].length()); x++) {
				zeroes += "0";
			}
			blocks[i] = "" + zeroes + blocks[i];
		}
		data[i] = blocks[i];
	    }
	}

	//Update method to just change the data associated with it, sets the dirty bit
	public void updateEntry(String[] blocks, int boffset){
	    this.data[boffset] = blocks[boffset];
	    makeDirty();
	}

	//Helper method for writing back an entry if it is dirty
	public void write2file(int address, memory mem){
	    for(int i = 0; i < this.data.length; i++){
		mem.setBlock( address + i, this.data[i]);
	    }
	}

	//returns whether this is a valid entry
	public boolean isValid(){
	    return this.valid;
	}

	public int isValidNum(){
	    if(this.valid)
		return 1;
	    else return 0;
	}
	
	//Get the tag for this entry
	public String getTag(){
	    return this.tag;
	}
	
	//Set the dirty bit on this entry
	public void makeDirty() {
	    this.dirty = true;
	} 

	//Given a block offset, get the inner block from this entry 
	public String getWord(int blockoffset) {
	    return this.data[blockoffset];
	}	
	
	public boolean isDirty() {
	    return this.dirty;
	}
	
	public int isDirtyNum() {
	    if(this.dirty)
		return 1;
	    else return 0;
	}

	public int getAddress() {
	    return this.address;
	}
	
	public void updateAge() {
		this.age++;
	}
	
	public void resetAge() {
		
	}
	
	public int returnAge() {
		return this.age;
	}
	
	public String printTag(){
	    String tagtag = binary_to_hex(this.tag);
	    int temp = 8 - tagtag.length();
	    String zeroes = "";
	    for(int i = 0;  i < temp; i++){
		zeroes += "0";
	    }
	    return zeroes + binary_to_hex(this.tag);
	}
    }

    //A container class for cache data
    private static class set_block {
	private cache cacheObj;                   //A pointer to the parent cache object
	private cache_entry[] entries;
	private int blocks;                       //The number of words
	private int tagSize;                      //The size of our tags
	private int boffset;                      //block offset size
	private int index;                        //The number of index bits
	private cache_entry LRU;                  //The cache_entry that is least recently used
	private cache parent;                     //The cache object that points to this set
	/*
	 *  Constructor: creates a full, empty set
	 *  @param datablocks the amount of blocks per cache entry
	 *  @param numEntries the number of entries in the set
	 */
	public set_block(int datablocks, int numEntries, int boffset, int index, int tagsize, cache cachemem){
	    this.tagSize = tagsize;
	    this.boffset = boffset;
	    this.index = index;
	    this.entries = new cache_entry[numEntries];
	    this.blocks = datablocks;
	    this.parent = cachemem;
	    for(int i = 0; i < numEntries; i++){
			String array[] = new String[datablocks];
			for(int x = 0; x < datablocks; x++){
			    array[x] = "00000000";
			}
			this.entries[i] = new cache_entry(datablocks, "00000000", 0, array, false);
		}
	    this.LRU = new cache_entry(datablocks, "00000000", 0, new String[datablocks], false);
	}

	/*
	 *  Write an entry to this set. If it already exists, update it and set to dirty
	 *  @param address, the memory location we're writing from, binary string
	 *  @param data, the block of data passed in from 
	 */
	public int writeEntry(String address, String[] data, memory mem){ 
	    //Check to see if we're holding onto this address in this set
	    int entryIndex = findEntry(address);
	    int miss = 0;
	    //Address is in cache A.K.A Hit
	    if(entryIndex >= 0){ 
			//Update the entry with the new data, we use the address to find the correct offset
			cache_entry current = this.entries[entryIndex];
			//So Update it's age as it's the most recently used
	    	updateLRU(current);
			int boffset = binary_to_int(address.substring(tagSize + index));
			current.updateEntry(data, boffset);
	    }   else {
			//Address isn't in cache, A.K.A. Miss
			miss = 1;
			String newTag = address.substring(0, this.tagSize);
			int intAddress = binary_to_int(address);
			//Offset so entry has correct address
			int position = intAddress % this.blocks;
			intAddress -= position;
			cache_entry newEntry = new cache_entry(this.blocks, newTag, intAddress, data, true);
			newEntry.makeDirty();
			int emptyIdx = findEmptyIndex(); 
			//Find somewhere to put this block in the cache
			if(emptyIdx == -1){ 
				//No vacancy, we have to evict somebody
			    cache_entry leastRecent = getLRU();
				evict(leastRecent, newEntry, address, mem);  
			    //Otherwise set the new entry into the empty Index
			} else { this.entries[emptyIdx] = newEntry;}
			//Update to make sure that all entries stay in step
			updateLRU(newEntry);
	    }
	    return miss;
	}

	/*
	 *  Similar to writeEntry, except we're just inserting an entry into the cache
	 *  still perform an evict if set is full
	 *  @param address, the memory location we're writing
	 *  @param data, the block of data passed in from 
	 */
	public cache_entry insertEntry(String address, String[] data, memory mem){
	    String tag = address.substring(0, this.tagSize);
	    int intAddr = binary_to_int(address);
		cache_entry newEntry = new cache_entry(this.blocks, tag, intAddr, data, true);		
	    int emptyIdx = this.findEmptyIndex(); //Now find somewhere to put this block in the cache
	    if(emptyIdx == -1){
			//No vacancy, we have to evict somebody
		    cache_entry leastRecent = getLRU();
			evict(leastRecent, newEntry, address, mem);     
	    } else {
			//Otherwise set the new entry into the empty Index
			this.entries[emptyIdx] = newEntry; 	  
	    }
	    updateLRU(newEntry);
	    return newEntry;
	}

	public void evict(cache_entry toevict, cache_entry entry, String address, memory mem){
	    //Before we remove lets check to see if this entry is dirty and if so, write back to memory
	    int oldIdx = findEntry(int_to_binary(toevict.getAddress()));
	    if (toevict.isDirty()){
			//Write back to memory
			//convert the binary string address to an integer value
			int intAddr = toevict.getAddress();
			toevict.write2file(intAddr, mem);
	    }
	    //Put the entry into the location that we just evicted
	    this.entries[oldIdx] = entry; 
	    //And update the cache's number of evicts
	    this.parent.updateEvict();
	}

	/*
	 * Read an entry from this set, returns an invalid block if the read fails
	 * @param address, the memory location we're looking for
	 */
	public cache_entry readEntry(String address, memory mem){
	    int entryIdx = findEntry(address);
	    if( entryIdx == -1){ 
	    	//If we read a miss we return an invalid entry
	    	return new cache_entry(this.blocks, "", 0, new String[this.blocks], false);
	    } else {
		    cache_entry returned = this.entries[entryIdx];
		    updateLRU(returned);
			return this.entries[entryIdx];
	    }
	}

	// Returns the index of an empty index in our entries array
	public int findEmptyIndex(){
	    for( int i = 0; i < entries.length; i++){
			cache_entry current = entries[i];
			if( !current.isValid() ){
			    return i;
			}
	    }
	    return -1;
	}

	// Helper to find an entry by address
	// I was going to convert the address to an int and compare it to the entries address
	// but I didn't think that was a true representation of the cache 
	private int findEntry(String address){
	    String tag = binary_to_hex(address.substring(0, this.tagSize));
	    for( int i = 0; i < this.entries.length; i++){
			cache_entry current = this.entries[i];
			String hexTag = binary_to_hex(current.getTag());
			if( hexTag.equals(tag)){
			    return i;
			}
	    }
	    return -1;
	}

	// Helper to find an entry by tag
	private int findTag(String tag){
	    String checktag = binary_to_hex(tag);
	    for( int i = 0; i < this.entries.length; i++){
		cache_entry current = this.entries[i];
		String hexTag = binary_to_hex(current.getTag());
			if( hexTag.equals(checktag)){
			    return i;
			}
	    }
	    return -1;
	}

	public void updateLRU(cache_entry updated){
		//Every time we use an entry we reset it's age to zero
		updated.resetAge();
		//Then we age everybody since we've just made an update
		for( int i = 0; i < entries.length; i++){
			cache_entry current = entries[i];
			current.updateAge();
			//If the current entry is older replace the LRU with it
			if(this.LRU.returnAge() < current.returnAge()){
				this.LRU = current;
			}
		}
	}
	
	public cache_entry getLRU(){
		//First we hold onto our oldest entry
		//Then we scan through our entries to find which it points to and find a replacement
		cache_entry replacement = new cache_entry(this.blocks, "", 0, new String[this.blocks], false);
		for( int i = 0; i < entries.length; i++){
			cache_entry current = entries[i];
			//If this entry is older than all the others we've scanned and he is not the current LRU, then hold onto him
			if((replacement.returnAge() < current.returnAge()) && (!current.equals(this.LRU))){
				replacement = current;
			}
		}
		cache_entry oldest = this.LRU;
		this.LRU = replacement;
		return oldest;
	}


	private cache_entry getEntry(int idx){
	    return this.entries[idx];
	}

	
    }
    
    private static class cache {
	private set_block[] sets;
	private int tagsize; //Size of tag bits
	private double n; //Set or Index bits
	private double m; //Block selection bits
	private int blocksize;
	private int assoc;
	private int read_attempts, write_attempts;
	private int num_sets, miss_total, miss_reads, miss_writes, num_evicted;
	private double  missrate_total, missrate_reads, missrate_writes;
	private memory sysMem;     //The memory object that we will be writing to and reading from

	public cache(int capacity, int blocksize, int associativity, memory mem){
	    //Set the memory object
	    this.sysMem = mem;
	    //Capacity comes in as kilobytes so multiply by (1024/16) or 64 to get the capacity in blocks
	    capacity *= 1024; //Capacity now in bytes
	    assoc = associativity;	   
        int numofentries = capacity / blocksize;
	    this.blocksize = blocksize /4;
	    numofentries /= associativity;
	    //System.out.println("We are making " + numofentries + " entries");
	    num_sets = numofentries;
	    sets = new set_block[num_sets];
	    this.m = Math.log(this.blocksize) / Math.log(2);
	    this.n = Math.log(num_sets) / Math.log(2);
	    this.tagsize = 32 - ((int)m + (int)n);
	    for( int i = 0; i < num_sets; i++){
	    	sets[i] = new set_block(this.blocksize, associativity, (int)m, (int)n, tagsize, this);
	    }
	    //Finally initialize just missed reads and writes, the rest can be instantiated when we run toString
	    this.miss_reads = 0;
	    this.miss_writes = 0;
	}
	/*
	 * This method attempts to write to cache, if we find that entry we write to it
	 * otherwise we just create a new entry, either way the dirty bit will be set
	 * @param address: the location that this entry will represent
	 * @param value  : the value to be written to cache
	 */
	public void cacheWrite(String address, String value){
	    String binaryAddr = hex_to_binary(address);
	    this.write_attempts++;
	    //Which set do we access?
	    int setLocation = binary_to_int( binaryAddr.substring(this.tagsize, this.tagsize+(int)n));
	    //System.out.println("Writing to set: " + setLocation);
	    set_block currentSet = this.sets[setLocation];
	    //Create a block of memory to pass to writeEntry
	    String[] memBlock = makeBlock(binaryAddr);
	    //update it with the new value
	    int memAddr = binary_to_int(binaryAddr);
	    
	    memBlock[memAddr % this.blocksize] = value;
	    //Write it to the cache and update the miss ratio
	    int hitormiss = currentSet.writeEntry(binaryAddr, memBlock, this.sysMem);
	    if( hitormiss > 0){
		addWriteMiss();
	    }
	}

	/*
	 * This method is used to construct a datablock from memory
	 * checks the datablock size to ensure we keep the entried aligned to that size
	 * @param startAddr: the location that we start building from, in binary
	 * @param mem      : the memory object that we will be reading from
	 */
	public String[] makeBlock(String startAddr){
	    //Mod the integer value of this address to get it's position in a datablock
	    int start = binary_to_int(startAddr);
	    int position = start % this.blocksize;
	    start -= position;
	    
	    String[] dataBlock = new String[this.blocksize];
	    for(int i = 0; i < this.blocksize; i++){
		dataBlock[i] = int_to_hex(this.sysMem.getBlock( start + i ));
	    }
	    return dataBlock;
	}

	/*
	 * This method is used to read from the cache, if the cache misses read from memory
	 * and create a new entry and add it to the cache
	 * @param address: the address that we're trying to find in cache
	 * @param mem    : the memory object that we will be reading from
	 */
	public String cacheRead(String address){
	    //Change it to a 32 bit address
	    this.read_attempts++;
	    String binaryAddr = hex_to_binary(address);
	    int setLocation = binary_to_int( binaryAddr.substring(this.tagsize, this.tagsize+(int)n));
	    set_block currentSet = this.sets[setLocation];
	    cache_entry attempt = currentSet.readEntry(binaryAddr, this.sysMem);
	    if( !attempt.isValid()){ 
		//If the attempt is an invalid block we need to read from memory and update the miss count
		addReadMiss();
		//Change the offset of the block so it fits our block alignment
		int startAddr = hex_to_int(address);
		int startOffset = startAddr % this.blocksize;
		//offsetBin is our offset binary address
		String offsetBin = int_to_binary(startAddr - startOffset);
		String[] memBlock = makeBlock(offsetBin);
		attempt = currentSet.insertEntry(binaryAddr, memBlock, this.sysMem);
	    }
	    //Get the block offset bits and convert to an int
	    int boffset = binary_to_int( binaryAddr.substring(32 - (int)m));
            return attempt.getWord(boffset);	    
	}


	public void updateEvict(){
	    this.num_evicted++;
	}

	public void addReadMiss(){
	    this.miss_reads++;
	}

	public void addWriteMiss(){
	    this.miss_writes++;
	}

	public void outputState(){
	    System.out.println("miss_reads: " + this.miss_reads + " miss_writes: " + this.miss_writes);
	    System.out.println("Tagsize: " + this.tagsize + " m: " + this.m + " n: " + this.n); 
	}

	public void writeToFile(){
	    for( int i = 0; i < this.sets.length; i++){
		set_block cSet = this.sets[i];
		for( int tEntry = 0; tEntry < assoc; tEntry++){
		    cache_entry cEntry = cSet.getEntry(tEntry);
		    int address = cEntry.getAddress();
		    cEntry.write2file(address, this.sysMem);
		}
	    }
	}



	public String toString(){
	    String result = "";
	    miss_total = miss_reads + miss_writes;
	    if (miss_reads != 0){
		missrate_reads = (double)miss_reads / read_attempts;
	    } else {
		missrate_reads = 0;
	    }
	    if (miss_writes != 0){
		missrate_writes = (double)miss_writes / write_attempts;
	    } else {
		missrate_writes = 0;
	    }
	    missrate_total = (double)(miss_writes + miss_reads) / (read_attempts + write_attempts);
	    result += "STATISTICS\n";
	    result += "Misses:\n";
	    result += "Total: " + miss_total + " DataReads: " + miss_reads + " DataWrites: " + miss_writes + "\n";
	    result += "Miss rate:\n";
	    result += "Total: " + String.format("%.4g", missrate_total) + " DataReads: " + String.format("%.4g", missrate_reads) + " DataWrites: " + String.format("%.4g", missrate_writes) + "\n";
	    result += "Number of Dirty Blocks Evicted from the Cache: " + num_evicted + "\n\n";
	    
	    result += "CACHE CONTENTS\n";
	    result += "Set   V  Tag       D  Words\n";
	
	    /*
	     *
	     * 	capacity (in bytes)/block size(bytes) = # of blocks
	     * 	# of blocks / set associativity = # of sets
	     * 	
	     */

	    //String.format("%.5g%n", 0.912385);

            for( int i = 0; i < num_sets; i++){
	    	for( int j = 0; j < assoc; j++ ) {
		    //Next few lines check the length of the set# and decide how to format it
		    String formati = "" + i;
		    int formatiLength = formati.length();
		    formati = int_to_hex(i).substring(8-formatiLength);
			if(formati.charAt(0) == '0' && formatiLength > 1) formati = formati.substring(1);	
		    cache_entry temp = sets[i].getEntry(j);
		    String spacer = "     ";
		    result += "" + formati + spacer.substring(formati.length()-1)  + temp.isValidNum() + "  " + temp.printTag() + "  " + temp.isDirtyNum();
		    for(int k = 0; k < blocksize; k++){
			result += "  " + temp.getWord(k);
		    }
		    result += "\n";
		}
	    }		 
	    return result;
	}
    }

    //Main memory
    /*
     *  @param size: the size of memory in Megabytes * 1024^2
     */
    private static class memory {
	private int[] data;

	public memory(int size){
	    data = new int[size];
	    for(int i=0; i<size; i++){
		data[i] = i;
	    }
	}

	public void setBlock(int address, String value){
	    data[address] = hex_to_int(value);
	}

	public int getBlock(int address){
	    return data[address];
	}
	
	public String toString(){
	    String result = "";
	    result += "MAIN MEMORY:\n";
	    result += "Address   Words\n";
	    
	    int start = cache_sim.hex_to_int("003f7e00");
       
	    for(int i=0; i < 1024; i++){
		result += int_to_hex(start + i*8);
		for(int j=0; j < 8; j++){
		    result += "  " + int_to_hex(getBlock(start + i*8 + j));
		}
		result += "\n";
	    }
	    return result;
	}
    }
    
    
    public static void main(String[] args) throws java.io.IOException {
	cache_sim c = new cache_sim();
	if(!c.parseParams(args)) {
	    return;
	}
	
	int i;
        int read_write;
        int address;
        int data; 

	memory mem = new memory(16777216); 
 
	// Initialize Cache
	cache cachemem = new cache(c.cache_capacity, c.cache_blocksize, c.cache_associativity, mem);

	String parseString;
	String[] strings;
	byte character;

	// Read until a newline
	while ((character = (byte)System.in.read()) != -1) {
	    parseString = "";

	    while(character != '\n') {
		parseString += (char)character;
		character = (byte)System.in.read();
	    }

	    strings = parseString.split("\\s");
	    if(strings.length < 2) {
		break;
	    }
	    // Read the first character of the line.
	    // It determines whether to read or write to the cache.
	    read_write = Integer.parseInt(strings[0]);

	    // check again if we have reached the end
	    // as this flag is set only after a 'cin'
	    //if(feof(stdin)) return 1;
	    //This pads the address in case they don't come in 8 bytes
	    String zeroes = "";
	    int difference = 8 - strings[1].length();
	    for( int x = 0; x < difference; x++ ){
	    	zeroes += "0";
	    }
	    strings[1] = "" + zeroes + strings[1]; 

	    // Read the address (as a hex number)
	    //address = hex_to_int(strings[1]);
	    //if it is a cache write the we have to read the data
	    if(read_write == CACHE_WRITE) {
		//data = hex_to_int(strings[2]);
		cachemem.cacheWrite(strings[1], strings[2]);		
		//output the new contents
	    } else {
		//Otherwise it's a cache read
		cachemem.cacheRead(strings[1]);
	    }
	    //System.out.println("memory[" + address + "] = " + mem.getBlock(address));        
	    
	}
	//cachemem.outputState();
	cachemem.writeToFile();
	//testMem(testArray, mem);
	System.out.println(cachemem);
	//System.out.println();
	System.out.println(mem);
    }

    public boolean parseParams(String[] args)
    {
	//needed for the parsing of command line options
	int c;
	boolean c_flag, b_flag, a_flag;
	boolean errflg = false;
	
	c_flag = b_flag = a_flag = errflg = false;
	
	
	for(int i = 0; i < args.length; i++) {
	    c = args[i].charAt(1);
	    
	    switch (c) {
	    case 'c':
                cache_capacity = Integer.parseInt(args[i].substring(2, args[i].length()));
                c_flag = true;
                break;
	    case 'b':
                cache_blocksize = Integer.parseInt(args[i].substring(2, args[i].length()));
                b_flag = true;
                break;
	    case 'a':
                cache_associativity = Integer.parseInt(args[i].substring(2, args[i].length()));
                a_flag = true;
                break;
	    case ':':       /* -c without operand */
		System.err.println("Option -" + c + " requires an operand\n");
                errflg = true;
                break;
	    case '?':
		System.err.println("Unrecognized option: -" + c + "\n");
                errflg=true;
	    }
	}
	
	//check if we have all the options and have no illegal options
	if(errflg || !c_flag || !b_flag || !a_flag) {
	    System.err.println("usage: java Cache -c<capacity> -b<blocksize> -a<associativity>\n");
	    return false;
	}
	
	return true;
	
    }

    public static void testMem(String[] s, memory mem){
	for( int i = 0; i < s.length; i++){
	    System.out.println("Memory [ " + s[i] + " ] is " +  int_to_hex(mem.getBlock( hex_to_int(s[i]))));
	}
    }

    public static int hex_to_int(String s) {
        String digits = "0123456789ABCDEF";
        s = s.toUpperCase();
        int val = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            int d = digits.indexOf(c);
            val = 16*val + d;
        }
        return val;
    }

    public static String hex_to_binary(String s) {
	String digits = "0123456789ABCDEF";
	String[] binaryArray = {"0000","0001","0010","0011",
				"0100","0101","0110","0111",
				"1000","1001","1010","1011",
				"1100","1101","1110","1111"};
	String result = "";
	s = s.toUpperCase();
	for (int i = 0; i < s.length(); i++){
	    char c = s.charAt(i);
	    result += binaryArray[digits.indexOf(c)];
	}
	return result;
    }

    public static int binary_to_int(String s) {
	String digits = "01";
	int val = 0;
	for (int i = 0; i < s.length(); i++){
	    char c = s.charAt(i);
	    int d = digits.indexOf(c);
	    val = 2*val + d;
	}
	return val;
    }

    public static String binary_to_hex(String s) {
	int baseten = binary_to_int(s);
	return Integer.toHexString(baseten);
    }

    public static String int_to_hex(int input_integer) {
	String hex_string;
	hex_string = Integer.toHexString(input_integer);
	while(hex_string.length()<8)
	    {
		hex_string = "0" + hex_string;
	    }
	
	return hex_string;
    }

    public static String int_to_binary(int input_integer) {
	String temp = int_to_hex(input_integer);
	return hex_to_binary(temp);
    }
}
