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
	    valid = isValid;
	    dirty = false;
	    data = new String[numblocks];
	    for(int i = 0; i < numblocks; i++){
		data[i] = blocks[i];
	    }
	}

	//Update method to just change the data associated with it, sets the dirty bit
	//TODO: smarter update entry for blocks within blocks
	public void updateEntry(String[] blocks){
	    for(int i = 0; i < this.data.length; i++){
		data[i] = blocks[i];
	    }
	    makeDirty();
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
	//TODO: this method
	private int getInnerBlock(String address) {
	    return -1;
	}	
	
    }

    //A container class for cache data
    private static class set_block {
	private cache_entry[] entries;
	private ArrayList<Integer> LRUcontainer;  //we store the whole address of the entry here 
	private int blocks;                       //The number of blocks that we hold in each entry
	private int tagSize;                      //The size of our tags
	private int boffset;
	private int index;
	/*
	 *  Constructor: creates a full, empty set
	 *  @param datablocks the amount of blocks per cache entry
	 *  @param numEntries the number of entries in the set
	 */
	public set_block(int datablocks, int numEntries, int boffset, int tagsize, int index){
	    tagSize = tagsize;
	    boffset = boffset;
	    index = index;
	    entries = new cache_entry[numEntries];
	    LRUcontainer = new ArrayList<Integer>(); //our LRU queue
	    blocks = datablocks;
	    for(int i = 0; i < numEntries; i++){
		entries[i] = new cache_entry(datablocks, "00000000", new String[datablocks], false);
	    }
	}

	/*
	 *  Write an entry to this set. If it already exists, update it and set to dirty
	 *  @param address, the memory location we're writing
	 *  @param data, the block of data passed in from 
	 */
	public void writeEntry(int address, String[] data){ 
	    //Check to see if we're holding onto this address in this set
	    int addressIdx = this.LRUcontainer.indexOf(address); // Return the index location of the address
	    String tempAddress = int_to_hex(address);

	    if(addressIdx != -1){ //Address is in cache A.K.A Hit
		//So move it to the end, because it's most recently use
		this.LRUcontainer.remove(addressIdx);  		
		this.LRUcontainer.add(address);
		
		String curTag = tempAddress.substring(0, this.tagSize + 1);
		int selectIdx = findEntry(tempAddress);
		cache_entry current = this.entries[selectIdx];
		current.updateEntry(data);

	    } else { //Address isn't in cache, A.K.A. Miss
		cache_entry newEntry = new cache_entry(this.blocks, tempAddress.substring(0, this.tagSize + 1), data, true);		
		int emptyIdx = findEmptyIndex(); //Now find somewhere to put this block in the cache
		
		if(emptyIdx == -1) evict(LRUcontainer.get(0), newEntry, tempAddress);     //No vacancy, we have to evict somebody		    
	    }
	    
	}
	//TODO: an evict also writes back to memory if the bit is dirty
	public void evict(int idx, cache_entry entry, String address){
	    LRUcontainer.remove(0);
	    LRUcontainer.add(cache_sim.hex_to_int(address));
	    String oldest = cache_sim.int_to_hex(idx);
	    //Now that we have an address we search through our entries, to find a match
	    String tag = oldest.substring(0, this.tagSize + 1);
	    int entryIdx = findEntry(address);
	    this.entries[entryIdx] = entry; //Put the entry into the location that we just evicted
	}

	/*
	 * Read an entry from this set, returns an invalid block if the read fails
	 * @param address, the memory location we're looking for
	 */
	public cache_entry readEntry(String address){
	    String tag = address.substring(0, this.tagSize + 1);
	    int entryIdx = findEntry(address);
	    if( entryIdx == -1){
		return new cache_entry(this.blocks, tag, new String[this.blocks], false);
	    } else {
		return this.entries[entryIdx];
	    }
	}

	// Simple helper method to find an empty block in this set
	private int findEmptyIndex(){
	    for( int i = 0; i < entries.length; i++){
		cache_entry current = entries[i];
		if( current.isValid() ){
		    return i;
		}
	    }
	    return -1;
	}

	// Helper to find an entry by tag
	private int findEntry(String address){
	    String tag = substring(0,this.tagSize + 1);
	    String offset = substring(this.tagSize + this.index, 31); // 31-32 is the two byte offset

	    for( int i = 0; i < entries.length; i++){
		cache_entry current = entries[i];
		if( current.getTag().equals(tag)){
		    return i;
		}
	    }
	    return -1;
	}

	

	
    }
    
    private static class cache {
	private set_block[] sets;
	private int blocksize;
	
	public cache(int capacity, int blocksize, int associativity){
	    //Capacity comes in as kilobytes so multiply by (1024/16) or 64 to get the capacity in blocks
	    capacity *= 64;
	    int numofentries = capacity / blocksize;
	    this.blocksize = blocksize;
	    numofentries /= associativity;
	    sets = new set_block[associativity];
	    m = Math.log(blocksize) / Math.log(2);
	    tagsize = 32 - (m + numofentries + 2);
	    for( int i = 0; i < associativity; i++){
		sets[i] = new set_block(blocksize, numofentries, m, tagsize, associativity);
	    }
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
	    int setLocation = intAddress % sets.length;
	    set_block currentSet = sets[setLocation];
	    //Create a block of memory to pass to writeEntry
	    String[] memBlock = makeBlock(address, mem);
	    currentSet.writeEntry(hex_to_int(address), memBlock);
	}

	/*
	 * This method is used to construct a datablock from memory
	 * @param startAddr: the location that we start building from
	 * @param mem      : the memory object that we will be reading from
	 */
	public String[] makeBlock(String startAddr, memory mem){
	    
	}

	/*
	 * This method is used to read from the cache, if the cache misses read from memory
	 * @param address: the address that we're trying to find in cache
	 * @param mem    : the memory object that we will be reading from
	 */
	public String cacheRead(String address, memory mem){
	    
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

	memory mem = new memory(8192000); 
 
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
	    address = hex_to_int(strings[1]);

	    //if it is a cache write the we have to read the data
	    if(read_write == CACHE_WRITE) {
		
		data = hex_to_int(strings[2]);
		mem.setBlock(address, data);
		
		//output the new contents
	    } 
	    System.out.println("memory[" + address + "] = " + mem.getBlock(address));        
	
	}
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

    public static String int_to_hex(int input_integer) {
	String hex_string;
	hex_string=Integer.toHexString(input_integer);
	while(hex_string.length()<8)
	    {
		hex_string = "0" + hex_string;
	    }
	
	return hex_string;
    }

}
