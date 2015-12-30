import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class Crawler {
	  private static final int MAX_PAGES_TO_SEARCH = 100;
	  private Set<String> pagesVisited = new HashSet<String>();
	  private List<String> pagesToVisit = new LinkedList<String>();
	  private Set<String> forbbidenPages = new HashSet<String>();
	  private int numberOfImages = 0;
	  private int totalSizeOfImages = 0;
	  private int numberOfVideos = 0;
	  private int totalSizeOfVideos = 0;
	  private int numberOfDocuments = 0;
	  private int totalSizeOfDocuments = 0;
	  private int numberOfPages = 0;
	  private int totalSizeOfPages = 0;
	  // Links from the same domain
	  private int numberOfInternalLinks = 0;
	  private int numberOfExternalLinks = 0;



	  public void search(String url)
	  {
	      while(this.pagesVisited.size() < MAX_PAGES_TO_SEARCH)
	      {
	          String currentUrl;
	          CrawlerLeg leg = new CrawlerLeg();
	          if(this.pagesToVisit.isEmpty())
	          {
	              currentUrl = url;
	              this.pagesVisited.add(url);
	          }
	          else
	          {
	              currentUrl = this.nextUrl();
	          }
	          boolean success = leg.crawl(currentUrl); // Lots of stuff happening here. Look at the crawl method in
	                                 // CrawlerLeg
	          
	          if(success)
	          {
	              System.out.println(String.format("**Success** Crawling %s has finished.", currentUrl));
	              this.pagesToVisit.addAll(leg.getLinks());
		          updateStatistics(leg);
	          }else{
	        	  System.out.println(String.format("**Failure** Crawling %s has not finished.", currentUrl));
	          }
	          
	      }
	      System.out.println("\n**Done** Visited " + this.pagesVisited.size() + " web page(s)");
	  }


	  /**
	   * Returns the next URL to visit (in the order that they were found). We also do a check to make
	   * sure this method doesn't return a URL that has already been visited.
	   * 
	   * @return
	   */
	  private String nextUrl()
	  {
	      String nextUrl;
	      do
	      {
	          nextUrl = this.pagesToVisit.remove(0);
	      } while(this.pagesVisited.contains(nextUrl));
	      this.pagesVisited.add(nextUrl);
	      return nextUrl;
	  }
	  
	  public void updateStatistics(CrawlerLeg leg){
		  updateImages(leg);
		  updateVideos(leg);
		  updateDocuments(leg);
		  updateImages(leg);
	  }
	  
	  public void updateImages(CrawlerLeg leg){
		  this.numberOfImages += leg.getNumberOfImages();
		  this.totalSizeOfImages += leg.getTotalSizeOfImages();
	  }
	  
	  public void updateVideos(CrawlerLeg leg){
		  this.numberOfVideos += leg.getNumberOfVideos();
		  this.totalSizeOfVideos += leg.getTotalSizeOfVideos();
	  }
	  
	  public void updateDocuments(CrawlerLeg leg){
		  this.numberOfDocuments += leg.getNumberOfDocuments();
		  this.totalSizeOfDocuments += leg.getTotalSizeOfDocuments();
	  }
	  
	  public void updatePages(CrawlerLeg leg){
		  this.numberOfPages += leg.getNumberOfPages();
		  this.totalSizeOfPages += leg.getTotalSizeOfPages();
	  }
}
