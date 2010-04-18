import java.util.ArrayList;

class cache_sim {

    private int cache_capacity;
    private int cache_blocksize;
    private int cache_associativity;

    //A class used to contain cache data
    private class cache_entry {
	private boolean valid;
	private string[] data;
	private string tag;
	
	//Constructor, uses deep copy on incoming block data
	public cache_entry(int numblocks, string tag, string[] blocks){
	    valid = true;
	    data = new int[numblocks];
	    for(i = 0; i < data.size(); i++){
		data[i] = blocks[i]
	    }
	}
	
    }

    //A container class for cache data
    private class set_block {
	private cache_entry[] entries;
	
	//Constructor creates a full, empty set
	/*
	 *  @param datablocks the amount of blocks per cache entry
	 */
	public set_block(int datablocks, int numEntries){
	    entries = new cache_entry[datablocks];
	    for(i = 0; i < numEntries; i++){
		entries[i] = new cache_entry(datablocks, '000000', new int[datablocks]);
	    }
	}
    }

    //Main memory
    private class Memory {
	private 
    }
    
    
    public static void main(String[] args) {
	cache_sim c = new cache_sim();
	if(!c.parseParams(args)) {
	    return;
	}
	
	io readwrite = new io();
        
	
	/*
	System.out.println("c is " + c.cache_capacity +
			   ", b is " + c.cache_blocksize + 
			   ", a is " + c.cache_associativity + "\n");*/
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

}
