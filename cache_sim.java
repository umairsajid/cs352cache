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
	public void updateEntry(String[] blocks){
	    for(int i = 0; i < this.data.length; i++){
		data[i] = blocks[i];
	    }
	    this.dirty = true;
	}

	//Simple helper method, returns whether this is a valid entry
	public boolean isValid(){
	    return this.valid;
	}
	
	//Get the tag for this entry
	public String getTag(){
	    return this.tag;
	}
	
    }

    //A container class for cache data
    private static class set_block {
	private cache_entry[] entries;
	private ArrayList<String> LRUcontainer;  //we store the whole address of the entry here 
	private int blocks;                       //The number of blocks that we hold in each entry
	private int tagSize;                      //The size of our tags
	
	/*
	 *  Constructor: creates a full, empty set
	 *  @param datablocks the amount of blocks per cache entry
	 *  @param numEntries the number of entries in the set
	 */
	public set_block(int datablocks, int numEntries){
	    entries = new cache_entry[numEntries];
	    ArrayList<Integer> LRUcontainer = new ArrayList<Integer>(); //our LRU queue
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
	public void writeEntry(String address, String[] data){
	    //Check to see if we're holding onto this address in this set
	    int addressIdx = this.LRUcontainer.indexOf(address);
	    if(addressIdx != -1){ //Address in cache
		this.LRUcontainer.remove(addressIdx);  //So move it to the end
		this.LRUcontainer.add(address);

		String curTag = address.substring(0, this.tagSize + 1);
		int selectIdx = findEntry(curTag);
		cache_entry current = this.entries[selectIdx];
		current.updateEntry(data);

	    } else {
		this.LRUcontainer.add(address);
		cache_entry newEntry = new cache_entry(this.blocks, address.substring(0, this.tagSize + 1), data, true);
	    
		//Now find somewhere to put this block in the cache
		int emptyIdx = findEmptyIndex();
		//TODO: this code needs to be turned into a method, since an evict also writes back to memory if the bit is dirty
		if( emptyIdx == -1){        //No vacancy, we have to evict somebody
		    //Check LRU
		    String oldest = this.LRUcontainer.get(0);  
		    //Now that we have an address we search through our entries, to find a match
		    String tag = oldest.substring(0, this.tagSize + 1);        //This is the tag we're looking for
		    int entryIdx = findEntry(tag);
		    this.entries[entryIdx] = newEntry;		
		}
		//END TODO:

	    }
	    
	}

	/*
	 * Read an entry from this set, returns an invalid block if the read fails
	 * @param address, the memory location we're looking for
	 */
	public cache_entry readEntry(String address){
	    String tag = address.substring(0, this.tagSize + 1);
	    int entryIdx = findEntry(tag);
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
	private int findEntry(String tag){
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
	
	public cache(int capacity, int blocksize, int associativity){
	    //Capacity comes in as kilobytes so multiply by (1024/16) or 64 to get the capacity in blocks
	    capacity *= 64;
	    int numofentries = capacity / blocksize;
	    numofentries /= associativity;
	    sets = new set_block[associativity];
	    for( int i = 0; i < associativity; i++){
		sets[i] = new set_block(blocksize, numofentries);
	    }

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