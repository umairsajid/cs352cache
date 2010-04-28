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
	
	/*
	 * Constructor, uses deep copy on incoming block data
	 * @param numblocks, the number of blocks this entry is expecting
	 * @param tag,       the tag for this entry
	 * @param blocks,    the datablock for this entry
	 * @param isValid,   is this a valid entry
	 */
	public cache_entry(int numblocks, String tag, String[] blocks, boolean isValid){
	    this.tag = tag;
	    valid = isValid;
	    dirty = false;
	    data = new String[numblocks];
	    for(int i = 0; i < numblocks; i++){
		data[i] = blocks[i];
	    }
	}

	//Update method to just change the data associated with it, sets the dirty bit
	public void updateEntry(String[] blocks){
	    for(int i = 0; i < this.data.length; i++){
		data[i] = blocks[i];
	    }
	    makeDirty();
	}

	//Helper method for writing back an entry if it is dirty
	public void write2file(int address, memory mem){
	    for(int i = 0; i < this.data.length; i++){
		mem.setBlock(address+i, hex_to_int(this.data[i]));
	    }
	}

	//Simple helper method, returns whether this is a valid entry
	public boolean isValid(){
	    return this.valid;
	}
	
	//Get the tag for this entry
	public String getTag(){
	    return this.tag;
	}

	public void makeDirty() {
	    this.dirty = true;
	}

	//Given an address and the parent tagSize, blockoffset and index, get the inner block from this entry 
	public String getWord(int blockoffset) {
	    return this.data[blockoffset];
	}	
	
	public boolean isDirty() {
	    return this.dirty;
	}
	
    }

    //A container class for cache data
    private static class set_block {
	private cache_entry[] entries;
	private ArrayList<Integer> LRUcontainer;  //we store the whole address of the entry here 
	private int blocks;                       //The number of words
	private int tagSize;                      //The size of our tags
	private int boffset;
	private int index;
	/*
	 *  Constructor: creates a full, empty set
	 *  @param datablocks the amount of blocks per cache entry
	 *  @param numEntries the number of entries in the set
	 */
	public set_block(int datablocks, int numEntries, int boffset, int tagsize, int index){
	    this.tagSize = tagsize;
	    this.boffset = boffset;
	    this.index = index;
	    this.entries = new cache_entry[numEntries];
	    this.LRUcontainer = new ArrayList<Integer>(); //our LRU queue
	    this.blocks = datablocks;
	    for(int i = 0; i < numEntries; i++){
		this.entries[i] = new cache_entry(datablocks, "00000000", new String[datablocks], false);
	    }
	}

	/*
	 *  Write an entry to this set. If it already exists, update it and set to dirty
	 *  @param address, the memory location we're writing
	 *  @param data, the block of data passed in from 
	 */
	public int writeEntry(int address, String[] data, memory mem){ 
	    //Check to see if we're holding onto this address in this set
	    int addressIdx = this.LRUcontainer.indexOf(address); // Return the index location of the address
	    String tempAddress = int_to_hex(address);
	    int miss = 0;

	    if(addressIdx != -1){ //Address is in cache A.K.A Hit
		//So move it to the end, because it's most recently used
		this.LRUcontainer.remove(addressIdx);  		
		this.LRUcontainer.add(address);
		
		String curTag = tempAddress.substring(0, this.tagSize + 1);
		int selectIdx = findEntry(tempAddress);
		cache_entry current = this.entries[selectIdx];
		current.updateEntry(data);
		miss = 1;

	    } else { //Address isn't in cache, A.K.A. Miss
		cache_entry newEntry = new cache_entry(this.blocks, tempAddress.substring(0, this.tagSize + 1), data, true);		
		int emptyIdx = findEmptyIndex(); //Now find somewhere to put this block in the cache
		
		if(emptyIdx == -1){
		    evict(LRUcontainer.get(0), newEntry, tempAddress, mem);     //No vacancy, we have to evict somebody
		} else {
		    this.entries[emptyIdx] = newEntry; 	  //Otherwise set the new entry into the empty Index
		}
		
	    }
	    return miss;
	}

	/*
	 *  Similar to writeEntry, except we're just inserting an entry into the cache
	 *  still perform an evict if set is full
	 *  @param address, the memory location we're writing
	 *  @param data, the block of data passed in from 
	 */
	public cache_entry insertEntry(int address, String[] data, memory mem){
	    String tempAddress = int_to_hex(address);
	    String tag = extractTag(tempAddress, this.tagSize);
	    cache_entry newEntry = new cache_entry(this.blocks, tag, data, true);		
	    int emptyIdx = findEmptyIndex(); //Now find somewhere to put this block in the cache
		
	    if(emptyIdx == -1){
		evict(LRUcontainer.get(0), newEntry, tempAddress, mem);     //No vacancy, we have to evict somebody
	    } else {
		this.entries[emptyIdx] = newEntry; 	  //Otherwise set the new entry into the empty Index
	    }
	    return newEntry;
	}

	public void evict(int toevict, cache_entry entry, String address, memory mem){
	    //Before we remove lets check to see if this entry is dirty and if so, write back to memory
	    String oldAddr = int_to_hex(toevict);
	    int oldIdx = findEntry(oldAddr);
	    cache_entry evicted = this.entries[oldIdx];
	    if (evicted.isDirty()){
		//Write back to memory
		evicted.write2file(toevict, mem);
	    }
	    LRUcontainer.remove(0);
	    LRUcontainer.add(cache_sim.hex_to_int(address)); //Add the new address to our LRU container
	    this.entries[oldIdx] = entry;                    //Put the entry into the location that we just evicted
	}

	/*
	 * Read an entry from this set, returns an invalid block if the read fails
	 * @param address, the memory location we're looking for
	 */
	public cache_entry readEntry(String address, memory mem){
	    int entryIdx = findEntry(address);
	    if( entryIdx == -1){ //If we read a miss we return an invalid entry
		return new cache_entry(this.blocks, "", new String[this.blocks], false);
	    } else {
		return this.entries[entryIdx];
	    }
	}

	// Simple helper method to find an empty block in this set
	private int findEmptyIndex(){
	    for( int i = 0; i < entries.length; i++){
		cache_entry current = entries[i];
		if( !current.isValid() ){
		    return i;
		}
	    }
	    return -1;
	}

	// Helper to find an entry by address
	private int findEntry(String address){
	    String binary = hex_to_binary(address);
	    String tag = binary_to_hex(binary.substring(0,this.tagSize + 1));

	    for( int i = 0; i < this.entries.length; i++){
		cache_entry current = this.entries[i];
		if( current.getTag().equals(tag)){
		    return i;
		}
	    }
	    return -1;
	}

	

	
    }
    
    private static class cache {
	private set_block[] sets;
	private int tagsize; //Size of tag bits
	private double n; //Set or Index bits
	private double m; //Block selection bits
	private int blocksize;
	private int miss_total, miss_reads, miss_writes, num_evicted;
	private double  missrate_total, missrate_reads, missrate_writes;

	public cache(int capacity, int blocksize, int associativity){
	    //Capacity comes in as kilobytes so multiply by (1024/16) or 64 to get the capacity in blocks
	    capacity *= 1024; //Capacity now in bytes
	    int numofentries = capacity / blocksize;
	    this.blocksize = blocksize/2;
	    numofentries /= associativity;
	    sets = new set_block[associativity];
	    this.m = Math.log(blocksize) / Math.log(2);
	    this.n = Math.log(associativity) / Math.log(2);
	    this.tagsize = 32 - ((int)m + (int)n + 2);
	    for( int i = 0; i < associativity; i++){
		sets[i] = new set_block(blocksize/2, numofentries, (int)m, tagsize, associativity);
	    }
	    //Finally initialize just missed reads and writes, the rest can be instantiated when we run toString
	    miss_reads = 0;
	    miss_writes = 0;
	}
	/*
	 * This method attempts to write to cache, if we find that entry we write to it
	 * otherwise we just create a new entry, either way the dirty bit will be set
	 * @param address: the location that this entry will represent
	 * @param value  : the value to be written to cache
	 */
	public void cacheWrite(String address, String value, memory mem){
	    int intAddress = hex_to_int(address);
	    //Which set do we access?
	    int setLocation = hex_to_int(address.substring(this.tagsize, this.tagsize + (int)n + 1));
	    set_block currentSet = this.sets[setLocation];
	    //Create a block of memory to pass to writeEntry, updating the newBlock
	    String[] memBlock = makeBlock(address, mem);
	    memBlock[0] = value;
	    int hitormiss = currentSet.writeEntry(hex_to_int(address), memBlock, mem);
	    this.miss_writes += hitormiss;
	}

	/*
	 * This method is used to construct a datablock from memory
	 * @param startAddr: the location that we start building from
	 * @param mem      : the memory object that we will be reading from
	 */
	public String[] makeBlock(String startAddr, memory mem){
	    String[] dataBlock = new String[this.blocksize];
	    for(int i = 0; i < this.blocksize; i++){
		dataBlock[i] = int_to_hex( mem.getBlock( hex_to_int(startAddr) + i ));
	    }
	    return dataBlock;
	}

	/*
	 * This method is used to read from the cache, if the cache misses read from memory
	 * and create a new entry and add it to the cache
	 * @param address: the address that we're trying to find in cache
	 * @param mem    : the memory object that we will be reading from
	 */
	public String cacheRead(String address, memory mem){
	    //TODO: get the right set block/ direct mapped block for this address
	    //Change it to a 32 bit address
	    String binaryaddr = hex_to_binary(address);
	    int setLocation = binary_to_int(binaryaddr.substring(this.tagsize, this.tagsize + (int)n));
	    set_block currentSet = this.sets[setLocation];
	    cache_entry attempt = currentSet.readEntry(address, mem);
	    if( !attempt.isValid()){ //If the attempt is an invalid block we need to read from memory and update the miss count
		this.miss_reads += 1;
		String[] memBlock = makeBlock(address, mem);
		attempt = currentSet.insertEntry(hex_to_int(address), memBlock, mem);
	    }
	    //Get the block offset bits and convert to an int
	    int boffset = binary_to_int( binaryaddr.substring(this.tagsize + (int)n + (int)m, 32));
            return attempt.getWord(boffset);	    
	}



	public String toString(){
	    String result = "";
	    miss_total = miss_reads + miss_writes;
	    if (miss_reads != 0){
		missrate_reads = (double)miss_reads / miss_total;
	    } else {
		missrate_reads = 0;
	    }
	    if (miss_writes != 0){
		missrate_writes = (double)miss_writes / miss_total;
	    } else {
		missrate_writes = 0;
	    }
	    result += "STATISTICS\n";
	    result += "Misses:\n";
	    result += "Total: " + miss_total + " DataReads: " + miss_reads + " DataWrites: " + miss_writes + "\n";
	    result += "Miss rate:\n";
	    result += "Total: " + missrate_total + " DataReads: " + missrate_reads + " DataWrites: " + missrate_writes + "\n";
	    result += "Number of Dirty Blocks Evicted from the Cache: " + num_evicted + "\n\n";
	    
	    result += "CACHE CONTENTS\n";
	    result += "Set\tV  Tag          D  Words";

	 
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

	public void setBlock(int address, int value){
	    data[address] = value;
	}

	public int getBlock(int address){
	    return data[address];
	}
	
	public String toString(){
	    String result = "";
	    result += "MAIN MEMORY:\n";
	    result += "Address  Words\n";
	    
	    int start = cache_sim.hex_to_int("003f7e00");
       
	    for(int i=0; i < 10; i++){
		result += int_to_hex(start + i*8);
		for(int j=0; j < 8; j++){
		    result += "  " +int_to_hex(getBlock(start + i*8 + j));
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
	cache cachemem = new cache(c.cache_capacity, c.cache_blocksize, c.cache_associativity);

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
	    //	    if(feof(stdin)) return 1;


	    // Read the address (as a hex number)
	    //address = hex_to_int(strings[1]);

	    //if it is a cache write the we have to read the data
	    if(read_write == CACHE_WRITE) {
		//data = hex_to_int(strings[2]);
		cachemem.cacheWrite(strings[1], strings[2], mem);		
		//output the new contents
	    } else {
		//Otherwise it's a cache read
		cachemem.cacheRead(strings[1], mem);
	    }
	    //System.out.println("memory[" + address + "] = " + mem.getBlock(address));        
	    
	}
	
	System.out.println(cachemem);
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
	hex_string=Integer.toHexString(input_integer);
	while(hex_string.length()<8)
	    {
		hex_string = "0" + hex_string;
	    }
	
	return hex_string;
    }

    public static String extractTag(String hex, int tagsize){
	String binary = hex_to_binary(hex);
	String btag = binary.substring(0, tagsize+1);
	return binary_to_hex(btag);
    }

}
