public class TypeStatistics {
	private int numberOfFileFromType;
	private int sizeOfFilesFromType;
	
	public TypeStatistics() {
		init();
	}
	
	public void init() {
		numberOfFileFromType = 0;
		sizeOfFilesFromType = 0;
	}
	
	public int getNumberOfFiles() {
		return numberOfFileFromType;
	}
	
	public int getSizeOfFiles() {
		return sizeOfFilesFromType;
	}
	
	public void addToFileCount(int num) {
		numberOfFileFromType += num;
	}
	
	public void addToFileSize(int num) {
		sizeOfFilesFromType += num;
	}
	
	public void updateCounter(int filesCount, int filesSize) {
		numberOfFileFromType +=filesCount;
		sizeOfFilesFromType +=filesSize;
	}

}
